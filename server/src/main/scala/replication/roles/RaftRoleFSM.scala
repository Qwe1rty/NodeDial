package replication.roles

import akka.actor.{ActorSystem, FSM}
import replication.{RaftEvent, RaftMessage, RaftRequest, RaftState}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


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
  with RPCTaskHandler[RaftMessage] {

  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  /**
   * Broadcast a new RequestVotes or AppendEntries request to all nodes in the Raft group.
   *
   * @param request the request
   */
  protected def publishRequest(request: RaftRequest): Set[Future[RaftEvent]]

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

  // Define the timeouts for each state
  // TODO

  // Define the state transitions
  // TODO

  initialize()

  private def onEvent[CurrentRole <: RaftRole](currentRole: CurrentRole): StateFunction = {

    case Event(event: RaftEvent, state: RaftState) =>
      val (rpcTask, newRole) = currentRole.processRaftEvent(event, state)

      handleRPCTask(rpcTask)
      goto(newRole).using(state)
  }

  override def handleRPCTask(RPCTask: RPCTask[RaftMessage]): Unit = {
    case ReplyTask(reply) => sender ! reply
    case BroadcastTask(message) => message match {
      case request: RaftRequest => publishRequest(request).foreach(_.onComplete {
        case Success(event) => self ! event
        case Failure(reason) => log.debug(s"RPC reply failed: ${reason.getLocalizedMessage}")
      })
    }
  }

}
