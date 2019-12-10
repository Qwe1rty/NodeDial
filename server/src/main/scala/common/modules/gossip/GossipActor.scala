package common.modules.gossip

import akka.actor.{Actor, ActorLogging, ActorRef}
import common.utils.ActorTimers.Tick
import common.utils.{ActorDefaults, ActorTimers}

import scala.concurrent.duration._


object GossipActor {

  case class Response()
}


class GossipActor(membershipActor: ActorRef) extends Actor with ActorLogging
                                                           with ActorDefaults
                                                           with ActorTimers {
  import GossipActor.Response
  import common.ChordialDefaults.INTERNAL_REQUEST_TIMEOUT

  start(100.millisecond)


  override def receive: Receive = {

    case Tick => {

    }

    case Response() => {

    }

    case x => log.error(receivedUnknown(x))
  }
}
