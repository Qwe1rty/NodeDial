package membership.gossip

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import akka.pattern.ask
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import com.risksense.ipaddr.IpAddress
import common.{DefaultActor, ServerDefaults}
import GossipSignal.{ClusterSizeReceived, SendRPC}
import common.membership.types.NodeState
import common.rpc.GRPCSettingsFactory
import common.utils.ActorTimers.Tick
import common.utils.ActorTimers
import membership.MembershipActor
import membership.api.{GetClusterSize, GetRandomNode, Membership}
import schema.ImplicitDataConversions._
import schema.PortConfiguration.MEMBERSHIP_PORT

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success}


object GossipActor extends GRPCSettingsFactory {

  private case class PayloadTracker(payload: GossipPayload, var count: Int, cooldown: Int) {

    def apply(grpcClientSettings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext): Unit = {
      payload.rpc(grpcClientSettings)(mat, ec)
      count -= 1
    }
  }


  def apply[KeyType: ClassTag]
      (membershipActor: ActorRef, delay: FiniteDuration, affiliation: String)
      (implicit actorSystem: ActorSystem): ActorRef = {

    actorSystem.actorOf(
      Props(new GossipActor[KeyType](membershipActor, delay, affiliation)),
      s"gossipActor-${affiliation}"
    )
  }

  override def createGRPCSettings
    (ipAddress: IpAddress, timeout: FiniteDuration)
    (implicit actorSystem: ActorSystem): GrpcClientSettings = {

    GrpcClientSettings
      .connectToServiceAt(
        ipAddress,
        MEMBERSHIP_PORT
      )
      .withDeadline(timeout)
  }
}


class GossipActor[KeyType: ClassTag] private(
    membershipActor: ActorRef,
    delay:           FiniteDuration,
    affiliation:     String
  )(
    implicit
    actorSystem: ActorSystem
  )
  extends DefaultActor
  with ActorLogging
  with ActorTimers {

  import GossipActor._

  implicit private val membershipAskTimeout: Timeout = delay // Semi-synchronous, can be bounded by cycle length
  implicit private val materializer: ActorMaterializer = ActorMaterializer()(context)
  implicit private val executionContext: ExecutionContext = actorSystem.dispatcher

  private val keyTable = mutable.Map[GossipKey[KeyType], PayloadTracker]()

  startPeriodic(delay)

  log.info(s"Gossip actor affiliated with ${affiliation} initialized")


  override def receive: Receive = {

    case Tick => keyTable.foreach { gossipEntry =>

      (membershipActor ? GetRandomNode(NodeState.ALIVE))
        .mapTo[Option[Membership]]
        .onComplete(randomMemberRequest => self ! SendRPC(gossipEntry._1, randomMemberRequest))
    }

    case SendRPC(key: GossipKey[KeyType], randomMemberRequest) => randomMemberRequest match {

      case Success(requestResult) => requestResult.foreach(member => if (member.nodeID != MembershipActor.nodeID) {
        val payload = keyTable(key)

        payload.count -= 1
        if (payload.count >= 0) payload(createGRPCSettings(member.ipAddress, delay * 2))
        if (payload.count <= payload.cooldown) keyTable -= key
      })

      case Failure(e) => log.error(s"Error encountered on membership node request: ${e}")
    }


    case GossipAPI.PublishRequest(key: GossipKey[KeyType], payload) => {
      log.debug(s"Gossip request received with key ${key}")

      (membershipActor ? GetClusterSize)
        .mapTo[Int]
        .onComplete(self ! ClusterSizeReceived(key, payload, _))
    }

    case ClusterSizeReceived(key: GossipKey[KeyType], payload, clusterSizeRequest) => clusterSizeRequest match {

      case Success(clusterSize) => if (!keyTable.contains(key)) {
        val bufferCapacity = ServerDefaults.bufferCapacity(clusterSize)

        log.debug(s"Cluster size detected as ${clusterSize}, setting gossip round buffer to ${bufferCapacity}")
        keyTable += key -> PayloadTracker(payload, bufferCapacity, -5 * bufferCapacity)
      }

      case Failure(e) => log.error(s"Cluster size request could not be completed: ${e}")
    }


    case x => log.error(receivedUnknown(x))
  }
}
