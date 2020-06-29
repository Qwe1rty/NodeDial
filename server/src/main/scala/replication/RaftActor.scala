package replication

import akka.actor.{Actor, ActorSystem}


abstract class RaftActor(implicit actorSystem: ActorSystem)
  extends Actor
  with ViewableRaftState {


  type LogEntryType

  def commit: Function[LogEntryType, Unit]


  override final def receive: Receive = ???
}
