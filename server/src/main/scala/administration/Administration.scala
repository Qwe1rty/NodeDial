package administration

import administration.Administration.AdministrationMessage
import administration.addresser.AddressRetriever
import administration.failureDetection.FailureDetector
import administration.gossip.Gossip.PublishRequest
import administration.gossip.{Gossip, GossipKey, GossipPayload}
import akka.actor
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.grpc.GrpcClientSettings
import common.ServerConstants
import common.administration.Event.EventType.Empty
import common.administration.Event.{EventType, Failure, Join, Refute, Suspect}
import common.administration._
import common.administration.types.NodeState.{ALIVE, DEAD, SUSPECT}
import common.administration.types.{NodeInfo, NodeState}
import org.slf4j.LoggerFactory
import partitioning.PartitionHashes
import schema.ImplicitDataConversions._
import schema.PortConfiguration.MEMBERSHIP_PORT

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Success, Try}


class Administration private(
    override protected val context: ActorContext[AdministrationMessage],
    protected val addressRetriever: AddressRetriever,
    protected var initializationCount: Int
  )
  extends AbstractBehavior[AdministrationMessage](context) {

  import Administration._

  implicit private val classicSystem: actor.ActorSystem = context.system.classicSystem
  implicit private val executionContext: ExecutionContext = context.system.executionContext

  private val gossip = context.spawn(Gossip[Event](context.self, 200.millisecond), "administration-gossip")
  context.log.info(s"Gossip component affiliated with administration initialized")

  protected var readiness: Boolean = false
  protected var subscribers: Set[ActorRef[AdministrationMessage]] = Set[ActorRef[AdministrationMessage]]()
  protected var membershipTable: MembershipTable =
    MembershipTable(NodeInfo(nodeID, addressRetriever.selfIP, 0, NodeState.ALIVE))

  AdministrationGRPCService(context.self)(context.system)
  context.spawn(FailureDetector(context.self), "failureDetector")

  context.log.info(s"Self IP has been detected to be ${addressRetriever.selfIP}")
  context.log.info("Administration component initialized")


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
  protected def publishExternally(event: Event): Unit = gossip ! PublishRequest[Event](
    GossipKey(event),
    GossipPayload(grpcClientSettings => (materializer, executionContext) =>
      AdministrationServiceClient(grpcClientSettings)(materializer, executionContext).publish(event)
  ))

  override def onMessage(msg: AdministrationMessage): Behavior[AdministrationMessage] = {
    msg match {

      /**
       * Event types that arrive from other nodes through the membership gRPC service
       */
      case event: Event => event.eventType match {
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

          membershipTable.get(event.nodeId).foreach(
            currentEntry =>
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

      /**
       * Internal API calls that relate to broadcasting/retrieving this node's state or information
       */
      case call: DeclarationCall => call match {

        case DeclareReadiness =>
          context.log.info("Membership readiness signal received")
          initializationCount -= 1

          if (initializationCount <= 0 && !readiness) {
            context.log.debug("Starting initialization sequence to establish readiness")

            // Only if the seed node is defined will there be any synchronization calls
            addressRetriever.seedIP match {
              case Some(seedIP) =>
                if (seedIP != addressRetriever.selfIP) {
                  context.log.info("Contacting seed node for membership listing")
                  AdministrationServiceClient(GrpcClientSettings.connectToServiceAt(seedIP, MEMBERSHIP_PORT))
                    .fullSync(FullSyncRequest(nodeID, addressRetriever.selfIP))
                    .onComplete(context.self ! SeedResponse(_))
                }
                else {
                  readiness = true
                  context.log.info("Seed IP was the same as this current node's IP, no full sync necessary")
                }
              case None =>
                readiness = true
                context.log.info("No seed node specified, will assume single-node cluster readiness")
            }
          }

        case DeclareEvent(nodeState, membershipPair) =>
          val targetID = membershipPair.nodeID
          val version = membershipTable.versionOf(targetID)
          context.log.info(s"Declaring node ${targetID} according to detected state ${nodeState}")

          val eventCandidate: Option[Event] = nodeState match {
            case NodeState.SUSPECT => Some(Event(targetID).withSuspect(Suspect(version)))
            case NodeState.DEAD => Some(Event(targetID).withFailure(Failure(version)))
            case _ => None
          }
          eventCandidate.foreach(
            event => {
              publishExternally(event)
              publishInternally(event)
            })

        case SeedResponse(syncResponse) => syncResponse match {

          case Success(response) =>
            membershipTable ++= response.syncInfo.map(_.nodeInfo)

            readiness = true
            context.log.info("Successful full sync response received from seed node")

            publishExternally(Event(nodeID).withJoin(Join(addressRetriever.selfIP, PartitionHashes(Nil))))
            context.log.info("Broadcasting join event to other nodes")

          case scala.util.Failure(e) =>
            context.log.error(s"Was unable to retrieve membership info from seed node: ${e}")

            context.self ! DeclareReadiness
            context.log.error("Attempting to reconnect with seed node")
        }
      }

      /**
       * Internal API calls that are getting cluster/admin information
       */
      case call: InformationCall => call match {
        case GetReadiness(replyTo) => replyTo ! readiness
        case GetClusterSize(replyTo) => replyTo ! membershipTable.size
        case GetClusterInfo(replyTo) => replyTo ! membershipTable.toSeq.map(SyncInfo(_, None))
        case GetRandomNode(nodeState, replyTo) => replyTo ! membershipTable.random(nodeState).lastOption
        case GetRandomNodes(nodeState, number, replyTo) => replyTo ! membershipTable.random(nodeState, number)
      }

      /**
       * Internal API calls that sub/unsub from cluster/admin events
       */
      case call: SubscriptionCall => call match {
        case Subscribe(actorRef) => subscribers += actorRef
        case Unsubscribe(actorRef) => subscribers -= actorRef
      }
    }; this
  }

}

object Administration {

  private val MEMBERSHIP_DIR       = ServerConstants.BASE_DIRECTORY/"administration"
  private val MEMBERSHIP_FILENAME  = "cluster"
  private val MEMBERSHIP_EXTENSION = ".info"
  private val MEMBERSHIP_FILE      = MEMBERSHIP_DIR/(MEMBERSHIP_FILENAME + MEMBERSHIP_EXTENSION)

  val rejoin: Boolean = !MEMBERSHIP_FILE.notExists
  val nodeID: String = NodeIDLoader(MEMBERSHIP_FILE)

  LoggerFactory.getLogger(Administration.getClass).info(
    s"Membership has determined node ID: $nodeID, with rejoin flag: $rejoin"
  )

  def apply(addressRetriever: AddressRetriever, initializationCount: Int): Behavior[AdministrationMessage] =
    Behaviors.setup(new Administration(_, addressRetriever, initializationCount))


  /** Actor protocol: includes the generated protobuf event types */
  trait AdministrationMessage
  sealed trait AdministrationAPI extends AdministrationMessage

  /** Actor protocol sub-class */
  private[administration] sealed trait DeclarationCall extends AdministrationAPI

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
  private[administration] case class SeedResponse(syncResponse: Try[SyncResponse]) extends DeclarationCall

  /** Actor protocol sub-class */
  private[administration] sealed trait InformationCall extends AdministrationAPI

  /**
   * Asks the membership actor whether or not the node is ready to receive client requests
   * Returns a `Boolean` value
   */
  final case class GetReadiness(replyTo: ActorRef[Boolean]) extends InformationCall

  /**
   * Get the current size of the cluster.
   * Returns an `Int` value
   */
  final case class GetClusterSize(replyTo: ActorRef[Int]) extends InformationCall

  /**
   * Get the full set of cluster information.
   * Returns a `Seq[NodeInfo]`
   */
  final case class GetClusterInfo(replyTo: ActorRef[Seq[SyncInfo]]) extends InformationCall

  /**
   * Requests a random node of the specified node state.
   * Returns an `Option[Membership]` object, which will be equal to `None` if there are
   * no other nodes in the cluster
   *
   * @param nodeState the state that the random node will be drawn from
   */
  final case class GetRandomNode(nodeState: NodeState, replyTo: ActorRef[Option[Membership]]) extends InformationCall

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
  final case class GetRandomNodes(nodeState: NodeState, number: Int, replyTo: ActorRef[Set[Membership]]) extends InformationCall

  /** Actor protocol sub-class */
  private[administration] sealed trait SubscriptionCall extends AdministrationAPI

  /**
   * Registers an actor to receive incoming event updates from the membership module
   *
   * @param actorRef actor reference
   */
  final case class Subscribe(actorRef: ActorRef[AdministrationMessage]) extends SubscriptionCall

  object Subscribe {

    def apply()(implicit actorRef: ActorRef[AdministrationMessage], d: Disambiguate.type): Subscribe =
      Subscribe(actorRef)
  }

  /**
   * Removes an actor from the membership module's event update list
   *
   * @param actorRef actor reference
   */
  final case class Unsubscribe(actorRef: ActorRef[AdministrationMessage]) extends SubscriptionCall

  object Unsubscribe {

    def apply()(implicit actorRef: ActorRef[AdministrationMessage], d: Disambiguate.type): Unsubscribe =
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
