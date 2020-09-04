package administration.gossip

import administration.Administration.{AdministrationMessage, GetClusterSize, GetRandomNode}
import administration.gossip.Gossip.GossipSignal
import administration.{Administration, Membership}
import akka.actor
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.grpc.GrpcClientSettings
import akka.util.Timeout
import com.risksense.ipaddr.IpAddress
import common.ServerDefaults
import common.administration.types.NodeState
import common.rpc.GRPCSettingsFactory
import schema.ImplicitDataConversions._
import schema.PortConfiguration.MEMBERSHIP_PORT

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


class Gossip[KeyType: ClassTag] private(
    override protected val context: ActorContext[GossipSignal[KeyType]],
    private val timer: TimerScheduler[GossipSignal[KeyType]],
    administration: ActorRef[AdministrationMessage],
    delay: FiniteDuration,
  )
  extends AbstractBehavior[GossipSignal[KeyType]](context) {

  import Gossip._

  implicit private val classicSystem: actor.ActorSystem = context.system.classicSystem
  implicit private val executionContext: ExecutionContext = context.system.classicSystem.dispatcher
  implicit private val membershipAskTimeout: Timeout = delay // Semi-synchronous, can be bounded by cycle length

  private val keyTable = mutable.Map[GossipKey[KeyType], PayloadTracker]()

  // Periodically broadcast all registered messages with provided delay
  timer.startTimerAtFixedRate(GossipTrigger, delay)


  override def onMessage(msg: GossipSignal[KeyType]): Behavior[GossipSignal[KeyType]] = {
    msg match {
      case GossipTrigger => for (gossipEntry <- keyTable) {
        context.ask(administration, GetRandomNode(NodeState.ALIVE, _: ActorRef[Option[Membership]])) {
          case Success(randomNode) => SendRPC(gossipEntry._1, randomNode)
          case Failure(e) =>
            context.log.error(s"Error encountered on membership node request: $e")
            SendRPC(gossipEntry._1, None)
        }
      }

      case SendRPC(key: GossipKey[KeyType], randomNode) => for (node <-randomNode) {
        if (node.nodeID != Administration.nodeID) {
          val payload = keyTable(key)

          payload.count -= 1
          if (payload.count >= 0) payload(createGRPCSettings(node.ipAddress, delay * 2))
          if (payload.count <= payload.cooldown) keyTable -= key
        }
      }

      case PublishRequest(key: GossipKey[KeyType], payload) =>
        context.log.debug(s"Gossip request received with key ${key}")
        context.ask(administration, GetClusterSize(_: ActorRef[Int])) {
          ClusterSizeReceived(key, payload, _)
        }

      case ClusterSizeReceived(key: GossipKey[KeyType], payload, clusterSizeRequest) => clusterSizeRequest match {
        case Failure(e) => context.log.error(s"Cluster size request could not be completed: ${e}")
        case Success(clusterSize) => if (!keyTable.contains(key)) {
          val bufferCapacity = ServerDefaults.bufferCapacity(clusterSize)
          context.log.debug(s"Cluster size detected as ${clusterSize}, setting gossip round buffer to ${bufferCapacity}")
          keyTable += key -> PayloadTracker(payload, bufferCapacity, -5 * bufferCapacity)
        }
      }

    }; this
  }

}

object Gossip extends GRPCSettingsFactory {

  def apply[KeyType: ClassTag]
  (administration: ActorRef[AdministrationMessage], delay: FiniteDuration): Behavior[GossipSignal[KeyType]] =
    Behaviors.setup(context => {
      Behaviors.withTimers(timer => {
        new Gossip[KeyType](context, timer, administration, delay)
      })
    })

  override def createGRPCSettings
  (ipAddress: IpAddress, timeout: FiniteDuration)(implicit actorSystem: ActorSystem): GrpcClientSettings =
    GrpcClientSettings.connectToServiceAt(ipAddress,MEMBERSHIP_PORT).withDeadline(timeout)


  /** Actor protocol */
  sealed trait GossipSignal[+KeyType]

  /**
   * Signal the gossip actor to publish a value to other random nodes for some
   * defined number of gossip cycles
   *
   * @param key key associated with publish task
   * @param payload the gRPC payload function to be called on
   */
  final case class PublishRequest[+KeyType](key: GossipKey[KeyType], payload: GossipPayload) extends GossipSignal[KeyType]

  private final case class ClusterSizeReceived[KeyType](key: GossipKey[KeyType],
    payload: GossipPayload,
    clusterSizeRequest: Try[Int]
  ) extends GossipSignal[KeyType]

  private final case object GossipTrigger
    extends GossipSignal[Nothing]

  private final case class SendRPC[KeyType](
    key: GossipKey[KeyType],
    randomMemberRequest: Option[Membership]
  ) extends GossipSignal[KeyType]

}