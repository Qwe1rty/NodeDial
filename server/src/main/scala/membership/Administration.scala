package membership

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import common.ServerConstants
import common.membership.Event.EventType.Empty
import common.membership.Event.{EventType, Refute}
import common.membership._
import common.membership.types.NodeState.{ALIVE, DEAD, SUSPECT}
import common.membership.types.{NodeInfo, NodeState}
import membership.Administration.AdministrationAPI
import membership.addresser.AddressRetriever
import membership.gossip.Gossip.PublishRequest
import membership.gossip.{Gossip, GossipKey, GossipPayload}
import membership.impl.InternalRequestDispatcher
import org.slf4j.LoggerFactory
import schema.ImplicitDataConversions._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try


class Administration private(
    private val context: ActorContext[AdministrationAPI],
    protected val addressRetriever: AddressRetriever,
    protected var initializationCount: Int
  )
  extends AbstractBehavior[AdministrationAPI](context)
  with InternalRequestDispatcher {

  import Administration._

  implicit private val actorSystem: ActorSystem[Nothing] = context.system
  implicit private val executionContext: ExecutionContext = actorSystem.executionContext

  private val gossipActor =
    context.spawn(Gossip[Event](context.self, 200.millisecond), "gossipActor-administration")
  context.log.info(s"Gossip actor affiliated with administration initialized")

  protected var readiness: Boolean = false
  protected var subscribers: Set[ActorRef] = Set[ActorRef]()
  protected var membershipTable: MembershipTable =
    MembershipTable(NodeInfo(nodeID, addressRetriever.selfIP, 0, NodeState.ALIVE))

  context.log.info(s"Self IP has been detected to be ${addressRetriever.selfIP}")
  context.log.info("Membership actor initialized")


  /**
   * Publish event to internal subscribers
   *
   * @param event event to publish
   */
  protected def publishInternally(event: Event): Unit = subscribers.foreach(_ ! event)

  /**
   * Publish event to other nodes via gossip
   *
   * @param event event to publish
   */
  protected def publishExternally(event: Event): Unit = gossipActor ! PublishRequest[Event](
    GossipKey(event),
    GossipPayload(grpcClientSettings => (materializer, executionContext) =>
      MembershipServiceClient(grpcClientSettings).publish(event)
  ))

  /**
   * Event types that arrive from other nodes through the membership gRPC service
   */
  private def receiveEvent(event: Event): Unit = {

    event.eventType match {

      case EventType.Join(joinInfo) =>
        context.log.debug(s"Join event - ${event.nodeId} - ${joinInfo}")

        if (!membershipTable.contains(event.nodeId)) {
          membershipTable += NodeInfo(event.nodeId, joinInfo.ipAddress, 0, ALIVE)
          context.log.info(s"New node ${event.nodeId} added to membership table with IP address ${joinInfo.ipAddress}")
        }
        else context.log.debug(s"Node ${event.nodeId} join event ignored, entry already in table")

        publishExternally(event)

      case EventType.Suspect(suspectInfo) =>
        context.log.debug(s"Suspect event - ${event.nodeId} - ${suspectInfo}")

        if (event.nodeId != nodeID && membershipTable.stateOf(event.nodeId) == NodeState.ALIVE) {
          membershipTable = membershipTable.updateState(event.nodeId, SUSPECT)
          context.log.debug(s"Node ${event.nodeId} will be marked as suspect")
        }
        else if (suspectInfo.version == membershipTable.versionOf(nodeID)) {
          membershipTable = membershipTable.incrementVersion(nodeID)
          context.log.debug(s"Received suspect message about self, will increment version and refute")

          publishExternally(Event(nodeID).withRefute(Refute(membershipTable.versionOf(nodeID))))
        }

      case EventType.Failure(failureInfo) =>
        context.log.debug(s"Failure event - ${event.nodeId} - ${failureInfo}")

        if (event.nodeId != nodeID && membershipTable.stateOf(event.nodeId) != NodeState.DEAD) {
          membershipTable = membershipTable.updateState(event.nodeId, DEAD)
          context.log.debug(s"Node ${event.nodeId} will be marked as dead")
        }
        else if (failureInfo.version == membershipTable.versionOf(nodeID)) { // TODO merge duplicates
          membershipTable = membershipTable.incrementVersion(nodeID)
          context.log.debug(s"Received death message about self, will increment version and refute")

          publishExternally(Event(nodeID).withRefute(Refute(membershipTable.versionOf(nodeID))))
        }

      case EventType.Refute(refuteInfo) =>
        context.log.debug(s"Refute event - ${event.nodeId} - ${refuteInfo}")

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
        context.log.debug(s"Leave event - ${event.nodeId}")

        if (membershipTable.contains(event.nodeId)) {
          membershipTable = membershipTable.unregister(event.nodeId)
          context.log.info(s"Node ${event.nodeId} declared intent to leave, removing from membership table")
        }
        publishExternally(event)

      case Empty => context.log.error(s"Received invalid Empty event - ${event.nodeId}")
    }

    publishInternally(event)
  }

  override def onMessage(msg: AdministrationAPI): Behavior[AdministrationAPI] = {
    // TODO
    this
  }

//  override def receive: Receive = {
//    case apiCall: AdministrationAPI => receiveAPICall(apiCall)
//    case event: Event           => receiveEvent(event)
//    case x                      => log.error(receivedUnknown(x))
//  }

}

object Administration {

  private val MEMBERSHIP_DIR       = ServerConstants.BASE_DIRECTORY/"membership"
  private val MEMBERSHIP_FILENAME  = "cluster"
  private val MEMBERSHIP_EXTENSION = ".info"
  private val MEMBERSHIP_FILE      = MEMBERSHIP_DIR/(MEMBERSHIP_FILENAME + MEMBERSHIP_EXTENSION)

  val rejoin: Boolean = !MEMBERSHIP_FILE.notExists
  val nodeID: String = NodeIDLoader(MEMBERSHIP_FILE)

  private val log = LoggerFactory.getLogger(Administration.getClass)
  log.info(s"Membership has determined node ID: ${nodeID}, with rejoin flag: ${rejoin}")

  def apply(addressRetriever: AddressRetriever, initializationCount: Int): Behavior[AdministrationAPI] =
    Behaviors.setup(new Administration(_, addressRetriever, initializationCount))


  /** Actor protocol: includes the generated protobuf event types */
  sealed trait AdministrationAPI

  /** Actor protocol sub-class */
  private[membership] sealed trait DeclarationCall extends AdministrationAPI

  /**
   * Signals the membership actor that a prerequisite service is ready (essentially a
   * "countdown" for the membership to start join procedure)
   * Does not return anything
   */
  final case object DeclareReadiness extends DeclarationCall

  /**
   * Signals the membership actor to broadcast the declaration across to the other nodes and
   * to internal subscribers.
   * Does not return anything
   *
   * @param nodeState state of the node
   * @param membershipPair node identifier
   */
  final case class DeclareEvent(nodeState: NodeState, membershipPair: Membership) extends DeclarationCall

  /**
   * A struct that represents the response received from the contacted seed node.
   *
   * @param syncResponse the seed node contact result
   */
  private[membership] case class SeedResponse(syncResponse: Try[SyncResponse]) extends DeclarationCall

  /** Actor protocol sub-class */
  private[membership] sealed trait InformationCall extends AdministrationAPI

  /**
   * Asks the membership actor whether or not the node is ready to receive client requests
   * Returns a `Boolean` value
   */
  final case object GetReadiness extends InformationCall

  /**
   * Get the current size of the cluster.
   * Returns an `Int` value
   */
  final case class GetClusterSize(replyTo: ActorRef[Int]) extends InformationCall

  /**
   * Get the full set of cluster information.
   * Returns a `Seq[NodeInfo]`
   */
  final case class GetClusterInfo(replyTo: ActorRef[Seq[SyncInfo]])
    extends InformationCall

  /**
   * Requests a random node of the specified node state.
   * Returns an `Option[Membership]` object, which will be equal to `None` if there are
   * no other nodes in the cluster
   *
   * @param nodeState the state that the random node will be drawn from
   */
  final case class GetRandomNode(nodeState: NodeState = NodeState.ALIVE, replyTo: ActorRef[Option[Membership]])
    extends InformationCall

  /**
   * Requests multiple random nodes of the specified node state.
   * Returns a `Set[Membership]` object, which will contain a max of `number` elements.
   *
   * (Unless there are fewer other nodes in the cluster, then the `Seq[Membership]` object
   * may contain less elements)
   *
   * @param number requested number of other random nodes
   * @param nodeState the state that the random nodes will be drawn from
   */
  final case class GetRandomNodes(nodeState: NodeState = NodeState.ALIVE, number: Int = 1, replyTo: ActorRef[Set[Membership]])
    extends InformationCall

  /** Actor protocol sub-class */
  private[membership] sealed trait SubscriptionCall extends AdministrationAPI

  /**
   * Registers an actor to receive incoming event updates from the membership module
   *
   * @param actorRef actor reference
   */
  final case class Subscribe(actorRef: ActorRef) extends SubscriptionCall

  object Subscribe {

    def apply()(implicit actorRef: ActorRef, d: Disambiguate.type): Subscribe =
      Subscribe(actorRef)
  }

  /**
   * Removes an actor from the membership module's event update list
   *
   * @param actorRef actor reference
   */
  final case class Unsubscribe(actorRef: ActorRef) extends SubscriptionCall

  object Unsubscribe {

    def apply()(implicit actorRef: ActorRef, d: Disambiguate.type): Unsubscribe =
      Unsubscribe(actorRef)
  }

  /**
   * An object that allows for the creation of the Subscribe and Unsubscribe objects through
   * implicit passing of the "self" field in an actor
   *
   * Since the companion object's "apply" function appears the same as the class constructors
   * after type erasure, this ensures that they are actually different as there's effectively
   * a new parameter
   */
  implicit final object Disambiguate
}
