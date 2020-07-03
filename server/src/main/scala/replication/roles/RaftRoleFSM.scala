package replication.roles

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, FSM}
import common.rpc.{BroadcastTask, RPCTask, RPCTaskHandler, ReplyTask}
import common.time._
import membership.MembershipActor
import membership.api.Membership
import replication.{RaftEvent, RaftMessage, RaftRequest, RaftState, RequestVoteRequest}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


/**
 * Defines FSM variables
 */
object RaftRoleFSM {

  val INITIAL_TIMEOUT_LOWER_BOUND: FiniteDuration = FiniteDuration(150, TimeUnit.MILLISECONDS)
  val INITIAL_TIMEOUT_UPPER_BOUND: FiniteDuration = FiniteDuration(325, TimeUnit.MILLISECONDS)
}


/**
 * The raft roles (Leader, Candidate, Follow) follow a finite state
 * machine pattern, so this trait encapsulates that logic. It includes the handling of
 * Raft events, both when the role state is stable and when the roles are transitioning.
 *
 * This FSM also includes the raft volatile and persistent state variables, and will
 * internally modify them as needed
 */
abstract class RaftRoleFSM(implicit actorSystem: ActorSystem)
  extends FSM[RaftRole, RaftState]
  with RPCTaskHandler[RaftMessage]
  with TimerTaskHandler[RaftTimeoutKey]
  with RaftGlobalTimeoutName {

  import RaftRoleFSM._
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  private var timeoutRange = TimeRange(INITIAL_TIMEOUT_LOWER_BOUND, INITIAL_TIMEOUT_UPPER_BOUND)


  // Will always start off as a Follower, even if it was a Candidate or Leader before.
  // All volatile raft state variables will be zero-initialized, but persisted states will
  // be read from file and restored.
  startWith(Follower, RaftState())

  // Define the event handling for all Raft roles, along with an error handling case
  when(Follower)(onReceive(Follower))
  when(Candidate)(onReceive(Candidate))
  when(Leader)(onReceive(Leader))

  // Define the state transitions
  // TODO: complete this
  onTransition {
    case Follower -> Candidate => nextStateData.currentTerm.read().foreach(currentTerm => {
      log.info(s"Starting leader election from term $currentTerm to ${currentTerm + 1}")

      nextStateData.currentTerm.increment()
      nextStateData.votedFor.write(MembershipActor.nodeID)

      handleTimerTask(ResetTimer(RaftGlobalTimeoutKey))
      handleRPCTask(BroadcastTask(RequestVoteRequest(currentTerm + 1, MembershipActor.nodeID, ???, ???)))
    })
    case Candidate -> Candidate => ???
  }

  // Initialize the FSM and start the global FSM timer
  initialize()
  handleTimerTask(ResetTimer(RaftGlobalTimeoutKey))

  log.info("Raft role FSM has been initialized, timer started")


  private def onReceive[CurrentRole <: RaftRole](currentRole: CurrentRole): StateFunction = {
    case Event(receive: Any, state: RaftState) =>

      val (rpcTask, timerTask, newRole) = receive match {
        case event: RaftEvent         => currentRole.processRaftEvent(event, state)
        case timeout: RaftTimeoutTick => currentRole.processRaftTimeout(timeout, state)
        case x =>
          log.error(s"Raft role FSM encountered unhandled event error, received ${x.getClass}")
          throw new IllegalArgumentException(s"Unknown type ${x.getClass} received by Raft FSM")
      }

      handleRPCTask(rpcTask)
      handleTimerTask(timerTask)
      goto(newRole).using(state)
  }

  override def handleRPCTask(rpcTask: RPCTask[RaftMessage]): Unit = {
    case ReplyTask(reply) => sender ! reply
    case BroadcastTask(message) => message match {
      case request: RaftRequest => broadcast(request).foreach(_.onComplete {
        case Success(event)  => self ! event
        case Failure(reason) => log.debug(s"RPC reply failed: ${reason.getLocalizedMessage}")
      })
    }
  }

  override def handleTimerTask(timerTask: TimerTask[RaftTimeoutKey]): Unit = {
    case SetRandomTimer(RaftGlobalTimeoutKey, lower, upper) => timeoutRange = TimeRange(lower, upper)
    case SetFixedTimer(RaftGlobalTimeoutKey, timeout)       => timeoutRange = TimeRange(timeout, timeout)
    case ResetTimer(key) => key match {
      case RaftGlobalTimeoutKey =>
        startSingleTimer(TIMER_NAME, RaftGlobalTimeoutTick, timeoutRange.random())
      case RaftIndividualTimeoutKey(node) =>
        startSingleTimer(node.nodeID, RaftIndividualTimeoutTick(node), timeoutRange.random())
    }
  }

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
}
