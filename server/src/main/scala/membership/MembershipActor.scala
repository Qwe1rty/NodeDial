package membership

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import akka.stream.ActorMaterializer
import com.roundeights.hasher.Implicits._
import common.ChordialConstants
import common.gossip.GossipAPI.PublishRequest
import common.gossip.{GossipActor, GossipKey, GossipPayload}
import common.membership.Event.EventType.Empty
import common.membership.Event.{EventType, Failure, Refute, Suspect}
import common.membership.types.NodeState.{ALIVE, DEAD, SUSPECT}
import common.membership.types.{NodeInfo, NodeState}
import common.membership._
import common.utils.ActorDefaults
import membership.addresser.AddressRetriever
import org.slf4j.LoggerFactory
import schema.ImplicitDataConversions._
import schema.ImplicitGrpcConversions._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Success, Try}


object MembershipActor {

  private case class SeedResponse(syncResponse: Try[SyncResponse])

  private val MEMBERSHIP_DIR       = ChordialConstants.BASE_DIRECTORY/"membership"
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

      (newID, true)
    }

    else (MEMBERSHIP_FILE.loadBytes, false)
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

  private var subscribers = Set[ActorRef]()
  private var membershipTable = MembershipTable() + NodeInfo(
    nodeID,
    addressRetriever.selfIP,
    0,
    NodeState.ALIVE
  )


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
          log.debug(s"Join event - ${event.nodeId} - ${joinInfo}")

          if (!membershipTable.contains(event.nodeId)) {
            membershipTable += NodeInfo(event.nodeId, joinInfo.ipAddress, 0, ALIVE)
          }
          publishExternally(event)

        case EventType.Suspect(suspectInfo) =>
          log.debug(s"Suspect event - ${event.nodeId} - ${suspectInfo}")

          if (event.nodeId != nodeID) {
            membershipTable = membershipTable.updated(event.nodeId, SUSPECT)
          }
          else if (suspectInfo.version == membershipTable.version(nodeID)) {
            membershipTable = membershipTable.increment(nodeID)
            publishExternally(Event(nodeID).withRefute(Refute(membershipTable.version(nodeID))))
          }

        case EventType.Failure(failureInfo) =>
          log.debug(s"Failure event - ${event.nodeId} - ${failureInfo}")

          if (event.nodeId != nodeID) {
            membershipTable = membershipTable.updated(event.nodeId, DEAD)
          }
          else if (failureInfo.version == membershipTable.version(nodeID)) { // TODO merge duplicates
            membershipTable = membershipTable.increment(nodeID)
            publishExternally(Event(nodeID).withRefute(Refute(membershipTable.version(nodeID))))
          }

        case EventType.Refute(refuteInfo) =>
          log.debug(s"Refute event - ${event.nodeId} - ${refuteInfo}")

          membershipTable.get(event.nodeId).foreach(currentEntry => {
            if (refuteInfo.version > currentEntry.version) {
              membershipTable = membershipTable.updated(NodeInfo(
                event.nodeId,
                membershipTable.address(event.nodeId),
                refuteInfo.version,
                NodeState.ALIVE
              ))
              publishExternally(event)
            }
          })

        case EventType.Leave(_) => {
          log.debug(s"Leave event - ${event.nodeId}")

          membershipTable -= event.nodeId
          publishExternally(event)
        }

        case Empty => log.error(s"Received invalid Empty event - ${event.nodeId}")
      }

      publishInternally(event)
    }


    case MembershipAPI.DeclareReadiness => {

      log.info("Membership readiness signal received")
      initializationCount -= 1

      if (initializationCount <= 0) addressRetriever.seedIP.foreach { seedIP =>

        if (seedIP != addressRetriever.selfIP) {
          log.info("Contacting seed node for membership listing")

          implicit val ec: ExecutionContext = actorSystem.dispatcher

          val grpcClientSettings = GrpcClientSettings.connectToServiceAt(
            seedIP,
            ChordialConstants.MEMBERSHIP_PORT
          )

          MembershipServiceClient(grpcClientSettings)(ActorMaterializer()(context), ec)
            .fullSync(FullSyncRequest(nodeID, addressRetriever.selfIP))
            .onComplete(self ! SeedResponse(_))
        }
        else log.info("Seed IP was the same as this current node's IP, no full sync necessary")

      }

      // TODO set internal readiness state and gRPC endpoint for clients/k8s to check
    }

    case SeedResponse(syncResponse) => syncResponse match {

      case Success(response) => {
        log.info("Successful full sync response received from seed node")
        membershipTable ++= response.syncInfo.map(_.nodeInfo)
      }

      case scala.util.Failure(e) => {
        log.error(s"Was unable to retrieve membership info from seed node: ${e}")

        self ! MembershipAPI.DeclareReadiness
        log.error("Attempting to reconnect with seed node")
      }
    }

    case MembershipAPI.DeclareEvent(nodeState, membershipPair) => {

      val targetID = membershipPair.nodeID
      val version = membershipTable.version(targetID)
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


    case MembershipAPI.GetClusterSize =>
      sender ! membershipTable.size

    case MembershipAPI.GetClusterInfo =>
      sender ! membershipTable.values.toSeq.map(SyncInfo(_, None))


    case MembershipAPI.GetRandomNode(nodeState) =>
      sender ! membershipTable.random(nodeState)

    case MembershipAPI.GetRandomNodes(nodeState, number) =>
      sender ! membershipTable.random(nodeState, number)


    case MembershipAPI.Subscribe(actorRef) =>
      subscribers += actorRef

    case MembershipAPI.Unsubscribe(actorRef) =>
      subscribers -= actorRef


    case x => log.error(receivedUnknown(x))
  }
}
