package common.modules.membership

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props, Timers}
import common.utils.ActorDefaults

import scala.concurrent.duration._


object FailureDetectorActor {

  private case object TickKey
  private case object Tick

  private val TICK_DELAY = 1.second


  private def props(membershipActor: ActorRef): Props =
    Props(new FailureDetectorActor(membershipActor))

  def apply(membershipActor: ActorRef)(implicit actorContext: ActorContext): ActorRef =
    actorContext.actorOf(props(membershipActor), "failureDetectorActor")
}


class FailureDetectorActor(membershipActor: ActorRef) extends Actor with ActorLogging
                                                                    with ActorDefaults
                                                                    with Timers {
  import FailureDetectorActor._

  timers.startTimerWithFixedDelay(TickKey, Tick, TICK_DELAY)


  override def receive: Receive = {

    case Tick => {
      // TODO: do a healthcheck
    }

    case x => log.error(receivedUnknown(x))
  }
}
