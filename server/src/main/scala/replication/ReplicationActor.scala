package replication

import akka.actor.{ActorRef, ActorSystem}


object ReplicationActor {

  def apply(persistenceActor: ActorRef)(implicit actorSystem: ActorSystem): ReplicationActor = {
    new ReplicationActor(persistenceActor)
  }
}


class ReplicationActor(persistenceActor: ActorRef)(implicit actorSystem: ActorSystem)
  extends RaftActor {

  override type LogEntryType = LogEntry

  override def commit: Function[LogEntry, Unit] = ???
}
