package common.modules.gossip

import akka.actor.{Actor, ActorLogging, ActorRef}
import common.utils.{ActorDefaults, ActorTimers}
import common.utils.ActorTimers.Tick

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


object GossipActor {

  case class Response()
}


class GossipActor
    (membershipActor: ActorRef)
    (implicit ec: ExecutionContext)
  extends Actor
  with ActorLogging
  with ActorDefaults
  with ActorTimers {

  import GossipActor.Response

  start(100.millisecond)


  override def receive: Receive = {

    case Tick => {

    }

    case Response() => {

    }

    case x => log.error(receivedUnknown(x))
  }
}
