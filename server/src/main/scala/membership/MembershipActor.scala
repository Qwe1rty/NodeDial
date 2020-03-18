package membership

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import akka.stream.ActorMaterializer
import com.roundeights.hasher.Implicits._
import common.ServerConstants
import common.gossip.GossipAPI.PublishRequest
import common.gossip.{GossipActor, GossipKey, GossipPayload}
import common.membership.Event.EventType.Empty
import common.membership.Event.{EventType, Failure, Join, Refute, Suspect}
import common.membership._
import common.membership.types.NodeState.{ALIVE, DEAD, SUSPECT}
import common.membership.types.{NodeInfo, NodeState}
import common.utils.ActorDefaults
import membership.addresser.AddressRetriever
import membership.api._
import org.slf4j.LoggerFactory
import partitioning.PartitionHashes
import schema.ImplicitDataConversions._
import schema.ImplicitGrpcConversions._
import schema.PortConfiguration.MEMBERSHIP_PORT

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Success


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
  val (nodeID: String, rejoin: Boolean) = {

    if (MEMBERSHIP_FILE.notExists) {
      val newID: String = System.nanoTime().toString.sha256
      log.info("Node ID not found - generating new ID")

      MEMBERSHIP_DIR.createDirectoryIfNotExists()
      MEMBERSHIP_FILE.writeByteArray(newID)

      (newID, false)
    }

    else (MEMBERSHIP_FILE.loadBytes, true)
  }
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


class MembershipActor private
    (addressRetriever: AddressRetriever, private var initializationCount: Int)
    (implicit actorSystem: ActorSystem)
  extends Actor
  with ActorLogging
  with ActorDefaults {

  import MembershipActor._

  private val gossipActor = GossipActor[Event](self, 200.millisecond, "membership")

  private var readiness = false
  private var subscribers = Set[ActorRef]()
  private var membershipTable: MembershipTable =
    MembershipTable(NodeInfo(nodeID, addressRetriever.selfIP, 0, NodeState.ALIVE))

  log.info(s"Self IP has been detected to be ${addressRetriever.selfIP}")
  log.info("Membership actor initialized")


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
  private def publishExternally(event: Event): Unit =
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

        membershipTable.get(event.nodeId).foreach(currentEntry => {
          if (refuteInfo.version > currentEntry.version) {
            membershipTable += NodeInfo(
              event.nodeId,
              membershipTable.addressOf(event.nodeId),
              refuteInfo.version,
              NodeState.ALIVE
            )
            publishExternally(event)
          }
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

  /**
   * Handle membership-altering requests from other components of the local node, that become
   * broadcasted to the rest of the cluster
   */
  private def receiveDeclarationCall: Function[DeclarationCall, Unit] = {

    case DeclareReadiness =>

      log.info("Membership readiness signal received")
      initializationCount -= 1

      if (initializationCount <= 0 && !readiness) {
        log.debug("Starting initialization sequence to establish readiness")

        // Only if the seed node is defined will there be any synchronization calls
        addressRetriever.seedIP match {

          case Some(seedIP) =>
            if (seedIP != addressRetriever.selfIP) {
              log.info("Contacting seed node for membership listing")

              implicit val ec: ExecutionContext = actorSystem.dispatcher

              val grpcClientSettings = GrpcClientSettings.connectToServiceAt(
                seedIP,
                MEMBERSHIP_PORT
              )

              MembershipServiceClient(grpcClientSettings)(ActorMaterializer()(context), ec)
                .fullSync(FullSyncRequest(nodeID, addressRetriever.selfIP))
                .onComplete(self ! SeedResponse(_))
            }
            else {
              readiness = true
              log.info("Seed IP was the same as this current node's IP, no full sync necessary")
            }

          case None =>
            readiness = true
            log.info("No seed node specified, will assume single-node cluster readiness")
        }
      }

    case DeclareEvent(nodeState, membershipPair) => {

      val targetID = membershipPair.nodeID
      val version = membershipTable.versionOf(targetID)
      log.info(s"Declaring node ${targetID} according to detected state ${nodeState}")

      val eventCandidate: Option[Event] = nodeState match {
        case NodeState.SUSPECT => Some(Event(targetID).withSuspect(Suspect(version)))
        case NodeState.DEAD =>    Some(Event(targetID).withFailure(Failure(version)))
        case _ =>                 None
      }

      eventCandidate.foreach(event => {
        publishExternally(event)
        publishInternally(event)
      })
    }

    case SeedResponse(syncResponse) => syncResponse match {

      case Success(response) =>
        membershipTable ++= response.syncInfo.map(_.nodeInfo)

        readiness = true
        log.info("Successful full sync response received from seed node")

        publishExternally(Event(nodeID).withJoin(Join(addressRetriever.selfIP, PartitionHashes(Nil))))
        log.info("Broadcasting join event to other nodes")

      case scala.util.Failure(e) =>
        log.error(s"Was unable to retrieve membership info from seed node: ${e}")

        self ! DeclareReadiness
        log.error("Attempting to reconnect with seed node")
    }
  }

  /**
   * Handle membership info get requests
   */
  private def receiveInformationCall: Function[InformationCall, Unit] = {

    case GetReadiness =>
      sender ! readiness

    case GetClusterSize =>
      sender ! membershipTable.size

    case GetClusterInfo =>
      sender ! membershipTable.toSeq.map(SyncInfo(_, None))

    case GetRandomNode(nodeState) =>
      sender ! membershipTable.random(nodeState).lastOption

    case GetRandomNodes(nodeState, number) =>
      sender ! membershipTable.random(nodeState, number)
  }

  /**
   * Handle subscription requests for observers to be informed of membership changes
   */
  private def receiveSubscriptionCall: Function[SubscriptionCall, Unit] = {

    case Subscribe(actorRef) =>
      subscribers += actorRef

    case Unsubscribe(actorRef) =>
      subscribers -= actorRef
  }

  override def receive: Receive = {
    case apiCall: MembershipAPI => apiCall match {
      case declarationCall: DeclarationCall   => receiveDeclarationCall(declarationCall)
      case informationCall: InformationCall   => receiveInformationCall(informationCall)
      case subscriptionCall: SubscriptionCall => receiveSubscriptionCall(subscriptionCall)
    }
    case event: Event => receiveEvent(event)
    case x            => log.error(receivedUnknown(x))
  }

}
