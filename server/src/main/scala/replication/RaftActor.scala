package replication

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, FSM}
import akka.stream.{ActorMaterializer, Materializer}
import akka.pattern.ask
import akka.util
import akka.util.Timeout
import common.persistence.Serializer
import common.rpc._
import common.time._
import membership.MembershipActor
import membership.api.Membership
import replication.Raft.{Commit, CommitConfirmation}
import replication.RaftServiceImpl.createGRPCSettings
import replication.eventlog.ReplicatedLog
import replication.roles.RaftRole.MessageResult
import replication.roles._
import replication.state._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


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
private[replication] class RaftActor[Command <: Serializable](
    private val initialState: RaftState,
    private val commitCallback: Commit[Command],
    private val serializer: Serializer[Command]
  )(
    implicit
    actorSystem: ActorSystem
  )
  extends FSM[RaftRole, RaftState]
  with RPCTaskHandler[RaftMessage]
  with TimerTaskHandler[RaftTimeoutKey]
  with RaftTimeouts {

  private[this] case class RaftCommitTick(commitResult: Try[CommitConfirmation]) // TODO move this elsewhere

  // Akka objects init
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  /**
   * Defines the upper and lower bounds of the randomized election timer. Defaults to the
   * publicly defined timeout range
   */
  private var TIMEOUT_RANGE: TimeRange = Raft.ELECTION_TIMEOUT_RANGE

  /**
   * The FSM timers use a string as a timer key, so the default election timer key name
   * is defined here
   */
  private val ELECTION_TIMER_NAME = "raftGlobalTimer"


  // Will always start off as a Follower on startup, even if it was a Candidate or Leader before.
  // All volatile raft state variables will be zero-initialized, but persisted states will
  // be read from file and restored.
  startWith(Follower, initialState)

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

        processTimerTask(SetRandomTimer(RaftGlobalTimeoutKey, Raft.ELECTION_TIMEOUT_RANGE))
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

      // Handle event message, one of 3 types: Raft message event, timeout event, or commit event
      val MessageResult(rpcTasks, timerTask, newRole) = receive match {
        case event:   RaftEvent       => currentRole.processRaftEvent(event, state)
        case timeout: RaftTimeoutTick => currentRole.processRaftTimeout(timeout, state)
        case persist: RaftCommitTick  => state.commitInProgress = false
          persist.commitResult match {
            case Success(_)         => state.lastApplied += 1
            case Failure(exception) => log.info(s"Commit result failure: ${exception.getLocalizedMessage}")
          }
        case x =>
          log.error(s"Raft role FSM encountered unhandled event error, received ${x.getClass}")
          throw new IllegalArgumentException(s"Unknown type ${x.getClass} received by Raft FSM")
      }

      // Handle any network or timer-related tasks as a result of applying the message
      rpcTasks.foreach(processRPCTask)
      processTimerTask(timerTask)

      // If there are still entries to commit, and there isn't one in progress (as we need to ensure
      // they are executed sequentially), then commit the next entry to the state machine
      if (!state.commitInProgress && state.lastApplied < state.commitIndex) {
        state.commitInProgress = true
        serializer.deserialize(state.log(state.lastApplied + 1)) match {
          case Success(logEntry)  => commitCallback(logEntry).onComplete(self ! RaftCommitTick(_))
          case Failure(exception) =>
            log.error(s"Deserialization error for log entry #${state.lastApplied + 1} commit: ${exception.getLocalizedMessage}")
            self ! RaftCommitTick(Failure(exception))
        }
      }

      // Switch roles, triggered as a result of timeouts or significant Raft events
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

    case ReplyFutureTask(task, node) => task match {
      case request: RaftRequest => sender ! message(request, node)
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
   * Send a new RPC request message to a specific node
   *
   * @param request the request
   * @return a future corresponding to a reply from a node
   */
  private def message(request: RaftRequest, node: Membership): Future[RaftResult] = {

    val client = RaftServiceClient(createGRPCSettings(
      node.ipAddress,
      request match {
        case _: AppendEntryEvent => FiniteDuration(5, TimeUnit.SECONDS)
        case _                   => Raft.INDIVIDUAL_NODE_TIMEOUT
      }
    ))

    val futureReply = request match {
      case appendEntryEvent: AppendEntryEvent         => client.newLogWrite(appendEntryEvent)
      case appendEntriesRequest: AppendEntriesRequest => client.appendEntries(appendEntriesRequest)
      case requestVoteRequest: RequestVoteRequest     => client.requestVote(requestVoteRequest)
      case _ =>
        Future.failed(new IllegalArgumentException("unknown Raft request type"))
    }

    startSingleTimer(node.nodeID, RaftIndividualTimeoutTick(node), Raft.INDIVIDUAL_NODE_TIMEOUT)
    futureReply
  }

  override def processTimerTask(timerTask: TimerTask[RaftTimeoutKey]): Unit = timerTask match {

    case SetRandomTimer(RaftGlobalTimeoutKey, timeRange) =>
      TIMEOUT_RANGE = timeRange
      startSingleTimer(ELECTION_TIMER_NAME, RaftGlobalTimeoutTick, TIMEOUT_RANGE.random())

    case SetFixedTimer(RaftGlobalTimeoutKey, timeout) =>
      TIMEOUT_RANGE = TimeRange(timeout, timeout)
      startSingleTimer(ELECTION_TIMER_NAME, RaftGlobalTimeoutTick, timeout)

    case CancelTimer(key) => key match {
      case RaftGlobalTimeoutKey           => cancelTimer(ELECTION_TIMER_NAME)
      case RaftIndividualTimeoutKey(node) => cancelTimer(node.nodeID)
    }

    case ResetTimer(key) => key match {
      case RaftGlobalTimeoutKey =>
        startSingleTimer(ELECTION_TIMER_NAME, RaftGlobalTimeoutTick, TIMEOUT_RANGE.random())
      case RaftIndividualTimeoutKey(node) =>
        startSingleTimer(node.nodeID, RaftIndividualTimeoutTick(node), Raft.INDIVIDUAL_NODE_TIMEOUT)
    }

    case ContinueTimer => () // no need to do anything
  }

}
