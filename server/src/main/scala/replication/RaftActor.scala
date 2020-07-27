package replication

import akka.actor.{ActorSystem, FSM}
import akka.stream.{ActorMaterializer, Materializer}
import common.persistence.Serializer
import common.rpc._
import common.time._
import membership.MembershipActor
import membership.api.Membership
import replication.RaftServiceImpl.createGRPCSettings
import replication.eventlog.ReplicatedLog
import replication.roles.RaftRole.MessageResult
import replication.roles._
import replication.state._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


/**
 * The raft roles (Leader, Candidate, Follow) follow a finite state
 * machine pattern, so this trait encapsulates that logic. It includes the handling of
 * Raft events, both when the role state is stable and when the roles are transitioning.
 *
 * This FSM also includes the raft volatile and persistent state variables, and will
 * internally modify them as needed
 *
 * @param actorSystem the actor system
 * @tparam Command the serializable type that will be replicated in the Raft log
 */
private[replication] abstract class RaftActor[Command <: Serializable](
    private val selfInfo: Membership,
    private val replicatedLog: ReplicatedLog
  )(
    implicit
    actorSystem: ActorSystem
  )
  extends FSM[RaftRole, RaftState]
  with RPCTaskHandler[RaftMessage]
  with TimerTaskHandler[RaftTimeoutKey]
  with RaftTimeouts {

  /**
   * The serializer is used to convert the log entry bytes to the command object, for when
   * Raft determines an entry needs to be committed
   */
  this: Serializer[Command] =>

  /**
   * The commit function is called after the Raft process has determined a majority of the
   * servers have agreed to append the log entry, and now needs to be interpreted by the
   * user code
   */
  type Commit = Function[Command, Unit]
  def commit: Commit


  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  private var timeoutRange: TimeRange = ELECTION_TIMEOUT_RANGE


  // Will always start off as a Follower on startup, even if it was a Candidate or Leader before.
  // All volatile raft state variables will be zero-initialized, but persisted states will
  // be read from file and restored.
  startWith(Follower, RaftState(selfInfo, replicatedLog))

  // Define the event handling for all Raft roles, along with an error handling case
  when(Follower)(onReceive(Follower))
  when(Candidate)(onReceive(Candidate))
  when(Leader)(onReceive(Leader))

  // Define the state transitions
  onTransition {

    case Follower -> Candidate | Candidate -> Candidate =>
      nextStateData.currentTerm.increment()
      nextStateData.currentTerm.read().foreach(currentTerm => {
        log.info(s"Starting leader election for new term: $currentTerm")

        nextStateData.votedFor.write(MembershipActor.nodeID)
        nextStateData.resetQuorum()

        processTimerTask(ResetTimer(RaftGlobalTimeoutKey))
        processRPCTask(BroadcastTask(RequestVoteRequest(
          currentTerm,
          MembershipActor.nodeID,
          nextStateData.log.lastLogIndex(),
          nextStateData.log.lastLogTerm()
        )))
      })

    case Candidate -> Follower =>
      nextStateData.currentTerm.read().foreach(currentTerm => {
        log.info(s"Stepping down from Candidate w/ term $currentTerm, after receiving ${nextStateData.numReplies()} votes")
      })

    case Leader -> Follower =>
      nextStateData.currentTerm.read().foreach(currentTerm => {
        log.info(s"Stepping down from Leader w/ term $currentTerm")

        processTimerTask(SetRandomTimer(RaftGlobalTimeoutKey, ELECTION_TIMEOUT_RANGE))
        processTimerTask(ResetTimer(RaftGlobalTimeoutKey))
      })

    case Candidate -> Leader =>
      nextStateData.currentTerm.read().foreach(currentTerm => {
        log.info(s"Election won, becoming leader of term $currentTerm")

        nextStateData.leaderState = RaftLeaderState(nextStateData.cluster(), nextStateData.log.size())

        processTimerTask(CancelTimer(RaftGlobalTimeoutKey))
      processRPCTask(BroadcastTask(AppendEntriesRequest(
          currentTerm,
          MembershipActor.nodeID,
          nextStateData.log.lastLogIndex(),
          nextStateData.log.lastLogTerm(),
          Seq.empty,
          nextStateData.commitIndex
        )))
      })
  }

  initialize()
  log.debug("Raft role FSM has been initialized")

  RaftServiceImpl(self)
  log.info("Raft API service has been initialized")

  processTimerTask(ResetTimer(RaftGlobalTimeoutKey))
  log.info("Randomized Raft election timeout started")


  private def onReceive[CurrentRole <: RaftRole](currentRole: CurrentRole): StateFunction = {
    case Event(receive: Any, state: RaftState) =>

      val initialCommitIndex = state.commitIndex

      val MessageResult(rpcTask, timerTask, newRole) = receive match {
        case event: RaftEvent         => currentRole.processRaftEvent(event, state)
        case timeout: RaftTimeoutTick => currentRole.processRaftTimeout(timeout, state)
        case x =>
          log.error(s"Raft role FSM encountered unhandled event error, received ${x.getClass}")
          throw new IllegalArgumentException(s"Unknown type ${x.getClass} received by Raft FSM")
      }

      if (state.commitIndex > initialCommitIndex) {
        for (index <- (initialCommitIndex + 1) until (state.commitIndex + 1)) deserialize(state.log(index)) match {
          case Success(logEntry) => commit(logEntry)
          case Failure(exception) =>
            log.error(s"Deserialization error occurred when committing log entry: ${exception.getLocalizedMessage}")
        }
      }

      processRPCTask(rpcTask)
      processTimerTask(timerTask)

      newRole match {
        case Some(role) => goto(role)
        case None       => stay
      }
  }

  /**
   * Make the network calls as dictated by the RPC task
   *
   * @param rpcTask the RPC task
   */
  override def processRPCTask(rpcTask: RPCTask[RaftMessage]): Unit = rpcTask match {
    case BroadcastTask(task) => task match {
      case request: RaftRequest => broadcast(request).foreach(_.onComplete {
        case Success(event)  => self ! event
        case Failure(reason) => log.debug(s"RPC request failed: ${reason.getLocalizedMessage}")
      })
    }
    case RequestTask(task, node) => task match {
      case request: RaftRequest => message(request, node).onComplete {
        case Success(event)  => self ! event
        case Failure(reason) => log.debug(s"RPC request failed: ${reason.getLocalizedMessage}")
      }
    }
    case ReplyTask(reply) => sender ! reply
  }

  /**
   * Broadcast a new RequestVotes or AppendEntries request to all nodes in the Raft group.
   *
   * @param request the request
   * @return set of futures, each future corresponding to a reply from a node
   */
  private def broadcast(request: RaftRequest): Set[Future[RaftResult]] =
    stateData.cluster().map(message(request, _)).toSet

  /**
   * Send a new RequestVotes or AppendEntries request message to a specific node
   *
   * @param request the request
   * @return a future corresponding to a reply from a node
   */
  private def message(request: RaftRequest, node: Membership): Future[RaftResult] = {

    val client = RaftServiceClient(createGRPCSettings(node.ipAddress, INDIVIDUAL_NODE_TIMEOUT))

    val futureReply = request match {
      case appendEntriesRequest: AppendEntriesRequest => client.appendEntries(appendEntriesRequest)
      case requestVoteRequest: RequestVoteRequest     => client.requestVote(requestVoteRequest)
      case _ =>
        Future.failed(new IllegalArgumentException("unknown Raft request type"))
    }

    startSingleTimer(node.nodeID, RaftIndividualTimeoutTick(node), INDIVIDUAL_NODE_TIMEOUT)
    futureReply
  }

  override def processTimerTask(timerTask: TimerTask[RaftTimeoutKey]): Unit = timerTask match {

    case SetRandomTimer(RaftGlobalTimeoutKey, timeRange) =>
      timeoutRange = timeRange
      startSingleTimer(ELECTION_TIMER_NAME, RaftGlobalTimeoutTick, timeoutRange.random())

    case SetFixedTimer(RaftGlobalTimeoutKey, timeout) =>
      timeoutRange = TimeRange(timeout, timeout)
      startSingleTimer(ELECTION_TIMER_NAME, RaftGlobalTimeoutTick, timeout)

    case ResetTimer(key) => key match {
      case RaftGlobalTimeoutKey =>
        startSingleTimer(ELECTION_TIMER_NAME, RaftGlobalTimeoutTick, timeoutRange.random())
      case RaftIndividualTimeoutKey(node) =>
        startSingleTimer(node.nodeID, RaftIndividualTimeoutTick(node), INDIVIDUAL_NODE_TIMEOUT)
    }

    case CancelTimer(key) => key match {
      case RaftGlobalTimeoutKey =>
        cancelTimer(ELECTION_TIMER_NAME)
      case RaftIndividualTimeoutKey(node) =>
        cancelTimer(node.nodeID)
    }

    case ContinueTimer => () // no need to do anything
  }

}
