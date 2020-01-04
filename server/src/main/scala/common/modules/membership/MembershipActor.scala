package common.modules.membership

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.roundeights.hasher.Implicits._
import common.ChordialConstants
import common.modules.addresser.AddressRetriever
import common.modules.membership.Event.EventType
import common.utils.ActorDefaults
import schema.ImplicitGrpcConversions._


object MembershipActor {

  private val MEMBERSHIP_DIR       = ChordialConstants.BASE_DIRECTORY/"membership"
  private val MEMBERSHIP_FILENAME  = "cluster"
  private val MEMBERSHIP_EXTENSION = ".info"
  private val MEMBERSHIP_FILE      = MEMBERSHIP_DIR/(MEMBERSHIP_FILENAME + MEMBERSHIP_EXTENSION)


  def apply(addressRetriever: AddressRetriever)(implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(
      Props(new MembershipActor(addressRetriever)),
      "membershipActor"
    )
}


class MembershipActor(addressRetriever: AddressRetriever) extends Actor
                                                          with ActorLogging
                                                          with ActorDefaults {
  import MembershipActor._

  private var subscribers = Set[ActorRef]()
  private var membershipTable = MembershipTable()

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

  //// wait on signal from partition actor to gossip join event (call RPC publish on seed node)


  override def receive: Receive = {

    // Event types that arrive from other nodes through the membership gRPC service
    case event: Event => {

      subscribers.foreach(_ ! event)
      event.eventType match {

        case EventType.Join(joinInfo) =>
          if (membershipTable.version(event.nodeId).isDefined) {
            sender ! Some(membershipTable(event.nodeId))
          }
          else {
            membershipTable += NodeInfo(event.nodeId, joinInfo.ipAddress, 0, NodeState.ALIVE)
            sender ! None
          }

        case EventType.Suspect(suspectInfo) =>


        case EventType.Failure(failureInfo) =>
        case EventType.Refute(refuteInfo) =>
        case EventType.Leave(_) =>
      }
    }

    case MembershipAPI.GetRandomNode(nodeState) =>
      sender ! None // TODO

    case MembershipAPI.GetRandomNodes(nodeState, number) =>
      sender ! Nil // TODO

    case MembershipAPI.Subscribe(actorRef) => subscribers += actorRef

    case MembershipAPI.Unsubscribe(actorRef) => subscribers -= actorRef

    case x => log.error(receivedUnknown(x))
  }
}
