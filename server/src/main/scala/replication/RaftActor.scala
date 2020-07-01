package replication

import akka.actor.ActorSystem
import replication.roles.RaftRoleFSM

import scala.concurrent.Future


abstract class RaftActor(implicit actorSystem: ActorSystem)
  extends RaftRoleFSM {

  type Commit = Function[AppendEntryEvent, Unit]

  /**
   * The commit function is called after the Raft process has determined a majority of the
   * servers have agreed to append the log entry, and now needs to be interpreted by the
   * user code
   */
  def commit: Commit

  /**
   * Broadcast a new RequestVotes or AppendEntries request to all nodes in the Raft group.
   *
   * @param request the request
   */
  override protected def publishRequest(request: RaftRequest): Set[Future[RaftEvent]] = ???
}
