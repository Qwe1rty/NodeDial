package replication

import akka.actor.ActorSystem
import replication.roles.RaftRoleFSM

import scala.concurrent.Future


abstract class RaftActor(implicit actorSystem: ActorSystem)
  extends RaftRoleFSM {

  type LogEntryType

  def commit: Function[LogEntryType, Unit]

  /**
   * Broadcast a new RequestVotes or AppendEntries request to all nodes in the Raft group.
   *
   * @param request the request
   */
  override protected def publishRequest(request: RaftRequest): Set[Future[RaftEvent]] = ???
}
