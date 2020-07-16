package replication

import akka.actor.{ActorSystem, FSM}
import common.persistence.Serializer
import common.rpc.{BroadcastTask, RPCTask, RPCTaskHandler, ReplyTask}
import common.time._
import membership.MembershipActor
import membership.api.Membership
import replication.eventlog.ReplicatedLog
import replication.roles.RaftRole.MessageResult
import replication.roles._

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

  this: Serializer[Command] =>

  implicit val executionContext: ExecutionContext = actorSystem.dispatcher
  var timeoutRange: TimeRange = ELECTION_TIMEOUT_RANGE


  /**
   * The commit function is called after the Raft process has determined a majority of the
   * servers have agreed to append the log entry, and now needs to be interpreted by the
   * user code
   */
  type Commit = Function[Command, Unit]
  def commit: Commit

  /**
   * Broadcast a new RequestVotes or AppendEntries request to all nodes in the Raft group.
   *
   * @param request the request
   * @return set of futures, each future corresponding to a reply from a node
   */
  protected def broadcast(request: RaftRequest): Set[Future[RaftEvent]]

  /**
   * Send a new RequestVotes or AppendEntries request to a specific node
   *
   * @param request the request
   * @return a future corresponding to a reply from a node
   */
  protected def request(request: RaftRequest, node: Membership): Future[RaftEvent]


  // Will always start off as a Follower, even if it was a Candidate or Leader before.
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
      nextStateData.currentTerm.read().foreach(currentTerm => {
        log.info(s"Starting leader election from term $currentTerm to ${currentTerm + 1}")

        nextStateData.currentTerm.increment()
        nextStateData.votedFor.write(MembershipActor.nodeID)
        nextStateData.votesReceived = 1

        handleTimerTask(ResetTimer(RaftGlobalTimeoutKey))
        handleRPCTask(BroadcastTask(RequestVoteRequest(currentTerm + 1, MembershipActor.nodeID, ???, ???)))
      })

    case Candidate -> Follower =>
      nextStateData.currentTerm.read().foreach(currentTerm => {
        log.info(s"Stepping down from Candidate w/ term $currentTerm, after receiving ${nextStateData.votesReceived} votes")
      })

    case Leader -> Follower =>
      nextStateData.currentTerm.read().foreach(currentTerm => {
        log.info(s"Stepping down from Leader w/ term $currentTerm")

        handleTimerTask(SetRandomTimer(RaftGlobalTimeoutKey, ELECTION_TIMEOUT_RANGE))
        handleTimerTask(ResetTimer(RaftGlobalTimeoutKey))
      })

    case Candidate -> Leader =>
      nextStateData.currentTerm.read().foreach(currentTerm => {
        log.info(s"Election won, becoming leader of term $currentTerm")

        handleTimerTask(CancelTimer(RaftGlobalTimeoutKey))

        // TODO broadcast AppendEntriesRequest
        // TODO set heartbeat timers for all nodes
      })
  }

  // Initialize the FSM and start the global FSM timer
  initialize()
  handleTimerTask(ResetTimer(RaftGlobalTimeoutKey))

  log.info("Raft role FSM has been initialized, election timer started")


  private def onReceive[CurrentRole <: RaftRole](currentRole: CurrentRole): StateFunction = {
    case Event(receive: Any, state: RaftState) =>

      val MessageResult(rpcTask, timerTask, newRole) = receive match {
        case event: RaftEvent         => currentRole.processRaftEvent(event, state)
        case timeout: RaftTimeoutTick => currentRole.processRaftTimeout(timeout, state)
        case x =>
          log.error(s"Raft role FSM encountered unhandled event error, received ${x.getClass}")
          throw new IllegalArgumentException(s"Unknown type ${x.getClass} received by Raft FSM")
      }

      handleRPCTask(rpcTask)
      handleTimerTask(timerTask)

      newRole match {
          case Some(role) => goto(role)
          case None       => stay
      }
  }

  override def handleRPCTask(rpcTask: RPCTask[RaftMessage]): Unit = rpcTask match {
    case ReplyTask(reply) => sender ! reply
    case BroadcastTask(message) => message match {
      case request: RaftRequest => broadcast(request).foreach(_.onComplete {
        case Success(event)  => self ! event
        case Failure(reason) => log.debug(s"RPC reply failed: ${reason.getLocalizedMessage}")
      })
    }
  }

  override def handleTimerTask(timerTask: TimerTask[RaftTimeoutKey]): Unit = timerTask match {

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
        startSingleTimer(node.nodeID, RaftIndividualTimeoutTick(node), timeoutRange.random())
    }

    case CancelTimer(RaftGlobalTimeoutKey) =>
      cancelTimer(ELECTION_TIMER_NAME)
  }

}
