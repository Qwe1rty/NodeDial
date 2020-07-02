package replication.roles

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, FSM, Timers}
import common.rpc.{BroadcastTask, RPCTask, RPCTaskHandler, ReplyTask}
import common.time.{ResetTimer, SetFixedTimer, SetRandomTimer, TimeRange, TimerTask, TimerTaskHandler}
import replication.{RaftEvent, RaftMessage, RaftRequest, RaftState}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


/**
 * Defines FSM variables
 */
object RaftRoleFSM {

  val TIMEOUT_LOWER_BOUND: FiniteDuration = FiniteDuration(150, TimeUnit.MILLISECONDS)
  val TIMEOUT_UPPER_BOUND: FiniteDuration = FiniteDuration(325, TimeUnit.MILLISECONDS)
}


/**
 * The raft roles (Leader, Candidate, Follow) follow a finite state
 * machine pattern, so this trait encapsulates that logic. It includes the handling of
 * Raft events, both when the role state is stable and when the roles are transitioning.
 *
 * This FSM also includes the raft volatile and persistent state variables, and will
 * internally modify them as needed
 */
abstract class RaftRoleFSM private[roles](implicit actorSystem: ActorSystem)
  extends FSM[RaftRole, RaftState]
  with RPCTaskHandler[RaftMessage]
  with TimerTaskHandler[RaftGlobalTimeoutKey.type]
  with RaftGlobalTimeoutName {

  import RaftRoleFSM._
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  private var timeoutRange = TimeRange(TIMEOUT_LOWER_BOUND, TIMEOUT_UPPER_BOUND)


  // Will always start off as a Follower, even if it was a Candidate or Leader before.
  // All volatile raft state variables will be zero-initialized, but persisted states will
  // be read from file and restored.
  startWith(Follower, RaftState())

  // Define the event handling for all Raft roles, along with an error handling case
  when(Follower)(onEvent(Follower))
  when(Candidate)(onEvent(Candidate))
  when(Leader)(onEvent(Leader))

  whenUnhandled {
    case _: Event =>
      log.error("Raft role FSM encountered unhandled event error")
      stay
  }

  // Define the state transitions
  // TODO

  initialize()
  handleTimerTask(ResetTimer(RaftGlobalTimeoutKey))

  log.info("Raft role FSM has been initialized, timer started")


  private def onEvent[CurrentRole <: RaftRole](currentRole: CurrentRole): StateFunction = {

    case Event(event: RaftEvent, state: RaftState) =>
      val (rpcTask, timerTask, newRole) = currentRole.processRaftEvent(event, state)

      handleRPCTask(rpcTask)
      handleTimerTask(timerTask)

      goto(newRole).using(state)
  }

  override def handleRPCTask(rpcTask: RPCTask[RaftMessage]): Unit = {
    case ReplyTask(reply) => sender ! reply
    case BroadcastTask(message) => message match {
      case request: RaftRequest => publishRequest(request).foreach(_.onComplete {
        case Success(event) => self ! event
        case Failure(reason) => log.debug(s"RPC reply failed: ${reason.getLocalizedMessage}")
      })
    }
  }

  override def handleTimerTask(timerTask: TimerTask[RaftGlobalTimeoutKey.type]): Unit = {
    case ResetTimer(_) => startSingleTimer(TIMER_NAME, RaftGlobalTimeoutTick, timeoutRange.random())
    case SetRandomTimer(_, lower, upper) => timeoutRange = TimeRange(lower, upper)
    case SetFixedTimer(_, timeout)       => timeoutRange = TimeRange(timeout, timeout)
  }

  /**
   * Broadcast a new RequestVotes or AppendEntries request to all nodes in the Raft group.
   *
   * @param request the request
   */
  protected def publishRequest(request: RaftRequest): Set[Future[RaftEvent]]
}
