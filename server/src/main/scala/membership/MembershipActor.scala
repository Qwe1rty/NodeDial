package membership

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import common.{DefaultActor, ServerConstants}
import common.membership.Event.EventType.Empty
import common.membership.Event.{EventType, Refute}
import common.membership._
import common.membership.types.NodeState.{ALIVE, DEAD, SUSPECT}
import common.membership.types.{NodeInfo, NodeState}
import membership.addresser.AddressRetriever
import membership.api._
import membership.gossip.GossipAPI.PublishRequest
import membership.gossip.{GossipActor, GossipKey, GossipPayload}
import membership.impl.InternalRequestDispatcher
import org.slf4j.LoggerFactory
import schema.ImplicitDataConversions._

import scala.concurrent.duration._


object MembershipActor {

  private val MEMBERSHIP_DIR       = ServerConstants.BASE_DIRECTORY/"membership"
  private val MEMBERSHIP_FILENAME  = "cluster"
  private val MEMBERSHIP_EXTENSION = ".info"
  private val MEMBERSHIP_FILE      = MEMBERSHIP_DIR/(MEMBERSHIP_FILENAME + MEMBERSHIP_EXTENSION)

  private val log = LoggerFactory.getLogger(MembershipActor.getClass)


  /*
   * Allow exception to propagate on nodeID file operations, to kill program and exit with
   * non-0 code. Must be allowed to succeed
   */
  val rejoin: Boolean = !MEMBERSHIP_FILE.notExists
  val nodeID: String = NodeIDLoader(MEMBERSHIP_FILE)

  log.info(s"Membership has determined node ID: ${nodeID}, with rejoin flag: ${rejoin}")


  def apply
      (addressRetriever: AddressRetriever, initializationCount: Int)
      (implicit actorSystem: ActorSystem): ActorRef = {

    actorSystem.actorOf(
      Props(new MembershipActor(addressRetriever, initializationCount)),
      "membershipActor"
    )
  }
}


class MembershipActor private(
    protected val clusterAddresses:    AddressRetriever,
    protected var initializationCount: Int
  )
  (implicit protected val actorSystem: ActorSystem)
  extends DefaultActor
  with ActorLogging
  with InternalRequestDispatcher {

  import MembershipActor._

  private val gossipActor = GossipActor[Event](self, 200.millisecond, "membership")

  protected var readiness: Boolean = false
  protected var subscribers: Set[ActorRef] = Set[ActorRef]()
  protected var membershipTable: MembershipTable =
    MembershipTable(NodeInfo(nodeID, clusterAddresses.selfIP, 0, NodeState.ALIVE))

  log.info(s"Self IP has been detected to be ${clusterAddresses.selfIP}")
  log.info("Membership actor initialized")


  /**
   * Publish event to internal subscribers
   *
   * @param event event to publish
   */
  protected def publishInternally(event: Event): Unit =
    subscribers.foreach(_ ! event)

  /**
   * Publish event to other nodes via gossip
   *
   * @param event event to publish
   */
  protected def publishExternally(event: Event): Unit =
    gossipActor ! PublishRequest[Event](
      GossipKey(event),
      GossipPayload(grpcClientSettings => (materializer, executionContext) =>
        MembershipServiceClient(grpcClientSettings)(materializer, executionContext)
          .publish(event)
      ))

  /**
   * Event types that arrive from other nodes through the membership gRPC service
   */
  private def receiveEvent(event: Event): Unit = {

    event.eventType match {

      case EventType.Join(joinInfo) =>
        log.debug(s"Join event - ${event.nodeId} - ${joinInfo}")

        if (!membershipTable.contains(event.nodeId)) {
          membershipTable += NodeInfo(event.nodeId, joinInfo.ipAddress, 0, ALIVE)
          log.info(s"New node ${event.nodeId} added to membership table with IP address ${joinInfo.ipAddress}")
        }
        else log.debug(s"Node ${event.nodeId} join event ignored, entry already in table")

        publishExternally(event)

      case EventType.Suspect(suspectInfo) =>
        log.debug(s"Suspect event - ${event.nodeId} - ${suspectInfo}")

        if (event.nodeId != nodeID && membershipTable.stateOf(event.nodeId) == NodeState.ALIVE) {
          membershipTable = membershipTable.updateState(event.nodeId, SUSPECT)
          log.debug(s"Node ${event.nodeId} will be marked as suspect")
        }
        else if (suspectInfo.version == membershipTable.versionOf(nodeID)) {
          membershipTable = membershipTable.incrementVersion(nodeID)
          log.debug(s"Received suspect message about self, will increment version and refute")

          publishExternally(Event(nodeID).withRefute(Refute(membershipTable.versionOf(nodeID))))
        }

      case EventType.Failure(failureInfo) =>
        log.debug(s"Failure event - ${event.nodeId} - ${failureInfo}")

        if (event.nodeId != nodeID && membershipTable.stateOf(event.nodeId) != NodeState.DEAD) {
          membershipTable = membershipTable.updateState(event.nodeId, DEAD)
          log.debug(s"Node ${event.nodeId} will be marked as dead")
        }
        else if (failureInfo.version == membershipTable.versionOf(nodeID)) { // TODO merge duplicates
          membershipTable = membershipTable.incrementVersion(nodeID)
          log.debug(s"Received death message about self, will increment version and refute")

          publishExternally(Event(nodeID).withRefute(Refute(membershipTable.versionOf(nodeID))))
        }

      case EventType.Refute(refuteInfo) =>
        log.debug(s"Refute event - ${event.nodeId} - ${refuteInfo}")

        membershipTable.get(event.nodeId).foreach(currentEntry =>
          if (refuteInfo.version > currentEntry.version) {
            membershipTable += NodeInfo(
              event.nodeId,
              membershipTable.addressOf(event.nodeId),
              refuteInfo.version,
              NodeState.ALIVE
            )
            publishExternally(event)
          })

      case EventType.Leave(_) =>
        log.debug(s"Leave event - ${event.nodeId}")

        if (membershipTable.contains(event.nodeId)) {
          membershipTable = membershipTable.unregister(event.nodeId)
          log.info(s"Node ${event.nodeId} declared intent to leave, removing from membership table")
        }
        publishExternally(event)

      case Empty => log.error(s"Received invalid Empty event - ${event.nodeId}")
    }

    publishInternally(event)
  }

  override def receive: Receive = {
    case apiCall: MembershipAPI => receiveAPICall(apiCall)
    case event: Event           => receiveEvent(event)
    case x                      => log.error(receivedUnknown(x))
  }

}
