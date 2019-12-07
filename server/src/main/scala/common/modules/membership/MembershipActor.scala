package common.modules.membership

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}


object MembershipActor {

  private def props: Props = Props(new MembershipActor)

  def apply()(implicit actorContext: ActorContext): ActorRef =
    actorContext.actorOf(props, "membershipActor")
}


class MembershipActor extends Actor with ActorLogging {



  override def receive: Receive = ???
}
