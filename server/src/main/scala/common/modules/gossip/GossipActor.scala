package common.modules.gossip

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import com.risksense.ipaddr.IpAddress
import common.modules.membership.{Membership, MembershipAPI, NodeState}
import common.utils.ActorTimers.Tick
import common.utils.{ActorDefaults, ActorTimers, GrpcSettingsFactory}
import akka.pattern.ask
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import common.ChordialDefaults
import common.modules.gossip.GossipSignal.{ClusterSizeReceived, SendRPC}

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}


object GossipActor extends GrpcSettingsFactory {

  private case class PayloadCount(payload: GossipPayload, var count: Int) {

    def apply(grpcClientSettings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext): Unit = {
      payload.rpc(grpcClientSettings)
      count -= 1
    }
  }


  def apply
      (membershipActor: ActorRef, delay: FiniteDuration, affiliation: String)
      (implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(
      Props(new GossipActor(membershipActor, delay)),
      s"gossipActor-${affiliation}"
    )

  override def createGrpcSettings
      (ipAddress: IpAddress, timeout: FiniteDuration)
      (implicit actorSystem: ActorSystem): GrpcClientSettings = {

    GrpcClientSettings
      .connectToServiceAt(
        ipAddress.toString,
        common.ChordialDefaults.MEMBERSHIP_PORT
      )
      .withDeadline(timeout)
  }
}


class GossipActor
    (membershipActor: ActorRef, delay: FiniteDuration)
    (implicit actorSystem: ActorSystem)
  extends Actor
  with ActorLogging
  with ActorDefaults
  with ActorTimers {

  import GossipActor._

  implicit private val membershipAskTimeout: Timeout = delay // Semi-synchronous, can be bounded by cycle length
  implicit private val materializer: ActorMaterializer = ActorMaterializer()(context)
  implicit private val executionContext: ExecutionContext = actorSystem.dispatcher

  private val keyTable = mutable.Map[GossipKey, PayloadCount]()

  start(delay)


  override def receive: Receive = {

    case Tick => keyTable.foreach { gossipEntry =>

      (membershipActor ? MembershipAPI.GetRandomNode(NodeState.ALIVE))
        .mapTo[Option[Membership]]
        .onComplete(randomMemberRequest => self ! SendRPC(gossipEntry._1, randomMemberRequest))
    }

    case SendRPC(key, randomMemberRequest) => randomMemberRequest match {

      case Success(requestResult) => requestResult.foreach(member => {
        keyTable(key)(createGrpcSettings(member.ipAddress, delay * 2))
        if (keyTable(key).count <= 0) keyTable -= key
      })

      case Failure(e) => log.error(s"Error encountered on membership node request: ${e}")
    }


    case GossipAPI.PublishRequest(key, payload) => {

      (membershipActor ? MembershipAPI.GetClusterSize)
        .mapTo[Int]
        .onComplete(self ! ClusterSizeReceived(key, payload, _))
    }

    case ClusterSizeReceived(key, payload, clusterSizeRequest) => clusterSizeRequest match {

      case Success(clusterSize) => keyTable += (key -> PayloadCount(
        payload,
        ChordialDefaults.bufferCapacity(clusterSize)
      ))

      case Failure(e) => log.error(s"Cluster size request could not be completed: ${e}")
    }


    case x => log.error(receivedUnknown(x))
  }
}
