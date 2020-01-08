package common.modules.gossip

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import com.risksense.ipaddr.IpAddress
import common.modules.membership.{Membership, MembershipAPI}
import common.utils.ActorTimers.Tick
import common.utils.{ActorDefaults, ActorTimers, GrpcSettingsFactory}
import akka.pattern.ask
import akka.util.Timeout
import common.modules.gossip.GossipSignal.SendRPC

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


object GossipActor extends GrpcSettingsFactory {

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

  implicit private val membershipAskTimeout: Timeout = delay
  implicit private val executionContext: ExecutionContext = actorSystem.dispatcher

  private val keyCounter = mutable.Map[GossipKey, Int]()

  start(delay)


  override def receive: Receive = {

    case Tick => keyCounter.foreach { gossipEntry =>

      (membershipActor ? MembershipAPI.GetRandomNode())
        .mapTo[Option[Membership]]
        .onComplete(member => self ! SendRPC(gossipEntry._1, member))
    }

    case SendRPC(key, member) => {
      
    }


    case GossipAPI.PublishRequest(key, count) => keyCounter += (key -> count)

    case x => log.error(receivedUnknown(x))
  }
}
