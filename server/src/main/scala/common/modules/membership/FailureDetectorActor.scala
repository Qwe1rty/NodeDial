package common.modules.membership

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}


object FailureDetectorActor {

  private def props(membershipActor: ActorRef): Props =
    Props(new FailureDetectorActor(membershipActor))

  def apply(membershipActor: ActorRef)(implicit actorContext: ActorContext): ActorRef =
    actorContext.actorOf(props(membershipActor), "failureDetectorActor")
}


class FailureDetectorActor(membershipActor: ActorRef) extends Actor with ActorLogging {



  override def receive: Receive = ???
}
