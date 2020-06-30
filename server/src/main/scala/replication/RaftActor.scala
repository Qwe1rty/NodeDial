package replication

import akka.actor.ActorSystem
import replication.roles.RaftRoleFSM


abstract class RaftActor(implicit actorSystem: ActorSystem)
  extends RaftRoleFSM {

  type LogEntryType

  def commit: Function[LogEntryType, Unit]


  override final def receive: Receive = ???
}
