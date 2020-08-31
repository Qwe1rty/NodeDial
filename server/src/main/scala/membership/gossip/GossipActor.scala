package membership.gossip

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.grpc.GrpcClientSettings
import akka.util.Timeout
import com.risksense.ipaddr.IpAddress
import common.ServerDefaults
import common.membership.types.NodeState
import common.rpc.GRPCSettingsFactory
import common.utils.ActorTimers
import common.utils.ActorTimers.Tick
import membership.{Administration, Membership}
import membership.Administration.{AdministrationAPI, GetClusterSize, GetRandomNode}
import membership.api.{GetClusterSize, GetRandomNode}
import membership.gossip.GossipActor.GossipSignal
import schema.ImplicitDataConversions._
import schema.PortConfiguration.MEMBERSHIP_PORT

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


object GossipActor extends GRPCSettingsFactory {

  def apply[KeyType: ClassTag]
    (administration: ActorRef[AdministrationAPI], delay: FiniteDuration): Behavior[GossipSignal[KeyType]] =
    Behaviors.setup(new GossipActor[KeyType](_, administration, delay))

  override def createGRPCSettings
    (ipAddress: IpAddress, timeout: FiniteDuration)(implicit actorSystem: ActorSystem): GrpcClientSettings = {

    GrpcClientSettings.connectToServiceAt(ipAddress,MEMBERSHIP_PORT).withDeadline(timeout)
  }

  /** Actor protocol */
  sealed trait GossipSignal[+KeyType]

  /**
   * Signal the gossip actor to publish a value to other random nodes for some
   * defined number of gossip cycles
   *
   * @param key key associated with publish task
   * @param payload the gRPC payload function to be called on
   */
  case class PublishRequest[+KeyType](key: GossipKey[KeyType], payload: GossipPayload) extends GossipSignal[KeyType]

  private case class SendRPC[KeyType](
    key: GossipKey[KeyType],
    randomMemberRequest: Try[Option[Membership]]
  ) extends GossipSignal[KeyType]

  private case class ClusterSizeReceived[KeyType](key: GossipKey[KeyType],
    payload: GossipPayload,
    clusterSizeRequest: Try[Int]
  ) extends GossipSignal[KeyType]

}

class GossipActor[KeyType: ClassTag] private(
    override private val context: ActorContext[GossipSignal[KeyType]],
    administration: ActorRef[AdministrationAPI],
    delay: FiniteDuration,
  )
  extends AbstractBehavior[GossipSignal[KeyType]](context)
  with ActorTimers {

  import GossipActor._

  implicit private val membershipAskTimeout: Timeout = delay // Semi-synchronous, can be bounded by cycle length
  implicit private val executionContext: ExecutionContext = context.system.classicSystem.dispatcher

  private val keyTable = mutable.Map[GossipKey[KeyType], PayloadTracker]()

  startPeriodic(delay)


  override def receive: Receive = {

    case Tick => keyTable.foreach { gossipEntry =>

      (administration ? GetRandomNode(NodeState.ALIVE))
        .mapTo[Option[Membership]]
        .onComplete(randomMemberRequest => self ! SendRPC(gossipEntry._1, randomMemberRequest))
    }

    case SendRPC(key: GossipKey[KeyType], randomMemberRequest) => randomMemberRequest match {

      case Success(requestResult) => requestResult.foreach(member => if (member.nodeID != Administration.nodeID) {
        val payload = keyTable(key)

        payload.count -= 1
        if (payload.count >= 0) payload(createGRPCSettings(member.ipAddress, delay * 2))
        if (payload.count <= payload.cooldown) keyTable -= key
      })

      case Failure(e) => context.log.error(s"Error encountered on membership node request: ${e}")
    }


    case PublishRequest(key: GossipKey[KeyType], payload) => {
      context.log.debug(s"Gossip request received with key ${key}")

      (administration ? GetClusterSize)
        .mapTo[Int]
        .onComplete(self ! ClusterSizeReceived(key, payload, _))
    }

    case ClusterSizeReceived(key: GossipKey[KeyType], payload, clusterSizeRequest) => clusterSizeRequest match {

      case Success(clusterSize) => if (!keyTable.contains(key)) {
        val bufferCapacity = ServerDefaults.bufferCapacity(clusterSize)

        context.log.debug(s"Cluster size detected as ${clusterSize}, setting gossip round buffer to ${bufferCapacity}")
        keyTable += key -> PayloadTracker(payload, bufferCapacity, -5 * bufferCapacity)
      }

      case Failure(e) => context.log.error(s"Cluster size request could not be completed: ${e}")
    }
  }
}
