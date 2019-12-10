package common.modules.membership

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import common.utils.ActorTimers.Tick
import common.utils.{ActorDefaults, ActorTimers}

import scala.concurrent.duration._


object FailureDetectorActor {

  private case class Response() {}


  private def props(membershipActor: ActorRef): Props =
    Props(new FailureDetectorActor(membershipActor))

  def apply(membershipActor: ActorRef)(implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(props(membershipActor), "failureDetectorActor")
}


class FailureDetectorActor(membershipActor: ActorRef) extends Actor with ActorLogging
                                                                    with ActorDefaults
                                                                    with ActorTimers {
  import FailureDetectorActor.Response
  import common.ChordialDefaults.INTERNAL_REQUEST_TIMEOUT

  start(2.second)


  override def receive: Receive = {

    case Tick => {

//      (membershipActor ? MembershipAPI.GetRandomNode).onComplete {
//
//      }
      // TODO: do a healthcheck, figure out which scheduler is best to use for gRPC calls
    }

    case Response() => {

    }

    case x => log.error(receivedUnknown(x))
  }
}
