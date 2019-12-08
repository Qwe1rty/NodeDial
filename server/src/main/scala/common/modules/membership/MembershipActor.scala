package common.modules.membership

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import better.files.File
import common.ChordialConstants


object MembershipActor {

  val MEMBERSHIP_DIR: File = ChordialConstants.BASE_DIRECTORY/"membership"


  private def props: Props = Props(new MembershipActor)

  def apply()(implicit actorContext: ActorContext): ActorRef =
    actorContext.actorOf(props, "membershipActor")
}


class MembershipActor extends Actor with ActorLogging {

  // initialize node table
  // initialize subscription service

  private var

  // check if membership dir exists
  // FALSE:
  //   generate new ID and persist
  // TRUE:
  //   read in ID

  // contact seed node for full sync

  //// wait on signal from partition actor to gossip join event

  override def receive: Receive = ???
}
