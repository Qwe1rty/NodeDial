package common.modules.membership

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.roundeights.hasher.Implicits._
import common.ChordialConstants
import common.modules.addresser.AddressRetriever
import common.modules.gossip.GossipAPI.PublishRequest
import common.modules.gossip.{GossipActor, GossipKey, GossipPayload}
import common.modules.membership.Event.{EventType, Refute}
import common.utils.ActorDefaults
import schema.ImplicitDataConversions._
import schema.ImplicitGrpcConversions._

import scala.concurrent.duration._

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


class MembershipActor
    (addressRetriever: AddressRetriever)
    (implicit actorSystem: ActorSystem)
  extends Actor
  with ActorLogging
  with ActorDefaults {

  import MembershipActor._

  private var subscribers = Set[ActorRef]()
  private var membershipTable = MembershipTable() // TODO make sure this has the current node ID at the very least

  // Allow exception to propagate on nodeID file operations, to kill program and exit with
  // non-0 code. Must be allowed to succeed
  private val nodeID: String = {

    if (MEMBERSHIP_FILE.notExists) {
      val newID: String = System.nanoTime().toString.sha256
      log.info("Node ID not found - generating new ID")

      MEMBERSHIP_DIR.createDirectoryIfNotExists()
      MEMBERSHIP_FILE.writeByteArray(newID)

      newID
    }

    else MEMBERSHIP_FILE.loadBytes
  }
  log.info(s"Membership has determined node ID: ${nodeID}")

  // TODO: contact seed node for full sync

  private val gossipActor = GossipActor(self, 200.millisecond, "membership")

  //// wait on signal from partition actor to gossip join event (call RPC publish on seed node)


  override def receive: Receive = {

    // Event types that arrive from other nodes through the membership gRPC service
    // TODO replace the "sender ! ..." pattern with separate gossip component
    case event: Event => {

      event.eventType match {

        case EventType.Join(joinInfo) =>
          log.debug(s"Join event - ${event.nodeId}")

          if (membershipTable.version(event.nodeId).isEmpty) {
            membershipTable += NodeInfo(event.nodeId, joinInfo.ipAddress, 0, NodeState.ALIVE)
          }
//          gossipActor ! PublishRequest(
//            GossipKey(nodeID),
//
//          )

        case EventType.Suspect(suspectInfo) =>
          log.debug(s"Suspect event - ${event.nodeId}")
          
          if (event.nodeId != nodeID) {
            membershipTable = membershipTable.updated(event.nodeId, NodeState.SUSPECT)
          }
          else if (suspectInfo.version == membershipTable.version(nodeID).get) {
            membershipTable = membershipTable.increment(nodeID)

            gossipActor ! PublishRequest(
              GossipKey(nodeID),
              GossipPayload(grpcClientSettings => (materializer, executionContext) => {
                  MembershipServiceClient(grpcClientSettings)(materializer, executionContext)
                    .publish(Event(nodeID).withRefute(Refute(membershipTable.version(nodeID).get)))
                }
              ))
          }

        case EventType.Failure(failureInfo) =>
          log.debug(s"Failure event - ${event.nodeId}")
          
          if (event.nodeId != nodeID) {
            membershipTable = membershipTable.updated(event.nodeId, NodeState.DEAD)
          }
          else if (failureInfo.version == membershipTable.version(nodeID).get) { // TODO merge duplicates
            membershipTable = membershipTable.increment(nodeID)

            gossipActor ! PublishRequest(
              GossipKey(nodeID),
              GossipPayload(grpcClientSettings => (materializer, executionContext) => {
                  MembershipServiceClient(grpcClientSettings)(materializer, executionContext)
                    .publish(Event(nodeID).withRefute(Refute(membershipTable.version(nodeID).get)))
                }
              ))
          }

        case EventType.Refute(refuteInfo) =>
          log.debug(s"Refute event - ${event.nodeId}")
          
          membershipTable.version(event.nodeId).foreach(localVersion => {
            if (refuteInfo.version > localVersion) membershipTable = membershipTable.updated(NodeInfo(
              event.nodeId,
              membershipTable.address(event.nodeId).get,
              refuteInfo.version,
              NodeState.ALIVE
            ))
          })

        case EventType.Leave(_) => {
          log.debug(s"Leave event - ${event.nodeId}")

          membershipTable -= nodeID
        }
      }

      subscribers.foreach(_ ! event)
    }


    case MembershipAPI.GetClusterSize => sender ! membershipTable.size

    case MembershipAPI.GetRandomNode(nodeState) => ???

    case MembershipAPI.GetRandomNodes(nodeState, number) => ???


    case MembershipAPI.Subscribe(actorRef) => subscribers += actorRef

    case MembershipAPI.Unsubscribe(actorRef) => subscribers -= actorRef


    case x => log.error(receivedUnknown(x))
  }
}
