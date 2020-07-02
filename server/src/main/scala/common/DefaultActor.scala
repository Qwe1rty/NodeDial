package common

import akka.actor.Actor


abstract class DefaultActor extends Actor {

  def receivedUnknown(x: Any): String =
    s"Unknown object of type ${x.getClass.getSimpleName} received"
}
