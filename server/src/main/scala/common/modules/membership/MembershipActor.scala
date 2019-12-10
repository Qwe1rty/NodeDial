package common.modules.membership

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import com.roundeights.hasher.Implicits._
import common.ChordialConstants
import schema.Implicits._


object MembershipActor {

  private val MEMBERSHIP_DIR       = ChordialConstants.BASE_DIRECTORY/"membership"
  private val MEMBERSHIP_FILENAME  = "cluster"
  private val MEMBERSHIP_EXTENSION = ".info"
  private val MEMBERSHIP_FILE      = MEMBERSHIP_DIR/(MEMBERSHIP_FILENAME + MEMBERSHIP_EXTENSION)


  private def props: Props = Props(new MembershipActor)

  def apply()(implicit actorContext: ActorContext): ActorRef =
    actorContext.actorOf(props, "membershipActor")
}


class MembershipActor extends Actor with ActorLogging {

  import MembershipActor._

  private var nodeTable = Map[String, Int]()
  private var subscribers = Set[ActorRef]()

  // Allow exception to propagate on nodeID file operations, to kill program and exit with
  // non-0 code. Must be allowed to succeed
  private val nodeID: String = {

    if (MEMBERSHIP_FILE.notExists) {
      val newID: String = System.nanoTime().toString.sha256

      MEMBERSHIP_DIR.createDirectoryIfNotExists()
      MEMBERSHIP_FILE.writeByteArray(newID)

      newID
    }

    else MEMBERSHIP_FILE.loadBytes
  }

  // TODO: contact seed node for full sync

  //// wait on signal from partition actor to gossip join event


  override def receive: Receive = {

    case MembershipAPI.GetRandomNode =>
      // TOOD get random

  }
}
