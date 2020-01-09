package common.membership

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.roundeights.hasher.Implicits._
import common.ChordialConstants
import common.gossip.GossipAPI.PublishRequest
import common.gossip.{GossipActor, GossipKey, GossipPayload}
import common.membership.Event.{EventType, Failure, Leave, Refute, Suspect}
import common.membership.addresser.AddressRetriever
import common.membership.types.{NodeInfo, NodeState}
import common.membership.types.NodeState.{ALIVE, DEAD, SUSPECT}
import common.utils.ActorDefaults
import schema.ImplicitDataConversions._
import schema.ImplicitGrpcConversions._

import scala.concurrent.duration._

object MembershipActor {

  private val MEMBERSHIP_DIR       = ChordialConstants.BASE_DIRECTORY/"membership"
  private val MEMBERSHIP_FILENAME  = "cluster"
  private val MEMBERSHIP_EXTENSION = ".info"
  private val MEMBERSHIP_FILE      = MEMBERSHIP_DIR/(MEMBERSHIP_FILENAME + MEMBERSHIP_EXTENSION)


  def apply
      (addressRetriever: AddressRetriever, initializationCount: Int)
      (implicit actorSystem: ActorSystem): ActorRef = {

    actorSystem.actorOf(
      Props(new MembershipActor(addressRetriever, initializationCount)),
      "membershipActor"
    )
  }
}


class MembershipActor
    (addressRetriever: AddressRetriever, initializationCount: Int)
    (implicit actorSystem: ActorSystem)
  extends Actor
  with ActorLogging
  with ActorDefaults {

  import MembershipActor._

  private var subscribers = Set[ActorRef]()
  private var membershipTable = MembershipTable() // TODO make sure this has the current node ID at the very least

  // Allow exception to propagate on nodeID file operations, to kill program and exit with
  // non-0 code. Must be allowed to succeed
  private val (nodeID: String, rejoin: Boolean) = {

    if (MEMBERSHIP_FILE.notExists) {
      val newID: String = System.nanoTime().toString.sha256
      log.info("Node ID not found - generating new ID")

      MEMBERSHIP_DIR.createDirectoryIfNotExists()
      MEMBERSHIP_FILE.writeByteArray(newID)

      (newID, true)
    }

    else (MEMBERSHIP_FILE.loadBytes, false)
  }
  log.info(s"Membership has determined node ID: ${nodeID}, with rejoin status: ${rejoin}")

  // TODO: contact seed node for full sync

  private val gossipActor = GossipActor[Event](self, 200.millisecond, "membership")

  //// wait on signal from partition actor to gossip join event (call RPC publish on seed node)


  /**
   * Publish event to internal subscribers
   *
   * @param event event to publish
   */
  private def publishInternally(event: Event): Unit =
    subscribers.foreach(_ ! event)

  /**
   * Publish event to other nodes via gossip
   *
   * @param event event to publish
   */
  private def publishExternally(event: Event): Unit = {

    gossipActor ! PublishRequest(
      GossipKey(event),
      GossipPayload(grpcClientSettings => (materializer, executionContext) =>
        MembershipServiceClient(grpcClientSettings)(materializer, executionContext)
          .publish(event)
      ))
  }

  override def receive: Receive = {

    // Event types that arrive from other nodes through the membership gRPC service
    case event: Event => {

      event.eventType match {

        case EventType.Join(joinInfo) =>
          log.debug(s"Join event - ${event.nodeId}")

          if (!membershipTable.contains(event.nodeId)) {
            membershipTable += NodeInfo(event.nodeId, joinInfo.ipAddress, 0, ALIVE)
          }
//          gossipActor ! PublishRequest(
//            GossipKey(nodeID),
//
//          )

        case EventType.Suspect(suspectInfo) =>
          log.debug(s"Suspect event - ${event.nodeId}")
          
          if (event.nodeId != nodeID) {
            membershipTable = membershipTable.updated(event.nodeId, SUSPECT)
          }
          else if (suspectInfo.version == membershipTable.version(nodeID)) {
            membershipTable = membershipTable.increment(nodeID)
            publishExternally(Event(nodeID).withRefute(Refute(membershipTable.version(nodeID))))
          }

        case EventType.Failure(failureInfo) =>
          log.debug(s"Failure event - ${event.nodeId}")
          
          if (event.nodeId != nodeID) {
            membershipTable = membershipTable.updated(event.nodeId, DEAD)
          }
          else if (failureInfo.version == membershipTable.version(nodeID)) { // TODO merge duplicates
            membershipTable = membershipTable.increment(nodeID)
            publishExternally(Event(nodeID).withRefute(Refute(membershipTable.version(nodeID))))
          }

        case EventType.Refute(refuteInfo) =>
          log.debug(s"Refute event - ${event.nodeId}")
          
          membershipTable.get(event.nodeId).foreach(currentEntry => {
            if (refuteInfo.version > currentEntry.version) {
              membershipTable = membershipTable.updated(NodeInfo(
                event.nodeId,
                membershipTable.address(event.nodeId),
                refuteInfo.version,
                NodeState.ALIVE
              ))
              publishExternally(Event(event.nodeId).withRefute(Refute(membershipTable.version(event.nodeId))))
            }
          })

        case EventType.Leave(_) => {
          log.debug(s"Leave event - ${event.nodeId}")

          membershipTable -= event.nodeId
          publishExternally(Event(event.nodeId).withLeave(Leave()) )
        }
      }

      publishInternally(event)
    }


    case MembershipAPI.GetClusterSize => sender ! membershipTable.size

    case MembershipAPI.GetRandomNode(nodeState) => ???

    case MembershipAPI.GetRandomNodes(nodeState, number) => ???


    case MembershipAPI.DeclareEvent(nodeState, membershipPair) => {

      val targetID = membershipPair.nodeID
      log.info(s"Declaring node ${targetID} according to detected state ${nodeState}")

      val eventCandidate: Option[Event] = nodeState match {
        case NodeState.SUSPECT => Some(Event(targetID).withSuspect(Suspect(membershipTable.version(targetID))))
        case NodeState.DEAD =>    Some(Event(targetID).withFailure(Failure(membershipTable.version(targetID))))
        case _ =>                 None
      }

      eventCandidate.foreach(event => {
        publishExternally(event)
        publishInternally(event)
      })
    }


    case MembershipAPI.Subscribe(actorRef) => subscribers += actorRef

    case MembershipAPI.Unsubscribe(actorRef) => subscribers -= actorRef


    case x => log.error(receivedUnknown(x))
  }
}
