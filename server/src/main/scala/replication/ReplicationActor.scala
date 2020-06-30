package replication

import akka.actor.{ActorRef, ActorSystem, Props}

import scala.concurrent.Future


object ReplicationActor {

  def apply
      (persistenceActor: ActorRef)
      (implicit actorSystem: ActorSystem): ActorRef = {

    actorSystem.actorOf(
      Props(new ReplicationActor(persistenceActor)),
      "replicationActor"
    )
  }
}


class ReplicationActor(persistenceActor: ActorRef)(implicit actorSystem: ActorSystem)
  extends RaftActor {

  override type LogEntryType = LogEntry

  override def commit: Function[LogEntry, Unit] = ???

  /**
   * Broadcast a new RequestVotes or AppendEntries request to all nodes in the Raft group.
   *
   * @param request the request
   */
  override protected def publishRequest(request: RaftRequest): Set[Future[RaftEvent]] = ???
}
