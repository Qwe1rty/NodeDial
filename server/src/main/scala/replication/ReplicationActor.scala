package replication

import akka.actor.{ActorRef, ActorSystem, Props}


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
}
