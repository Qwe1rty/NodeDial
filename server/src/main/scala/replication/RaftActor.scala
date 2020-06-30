package replication

import akka.actor.{Actor, ActorSystem}
import common.utils.DefaultActor
import replication.roles.RaftRoleFSM


abstract class RaftActor(implicit actorSystem: ActorSystem)
  extends DefaultActor
  with RaftRoleFSM {

  type LogEntryType

  def commit: Function[LogEntryType, Unit]


  override final def receive: Receive = ???
}
