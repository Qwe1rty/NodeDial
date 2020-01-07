package common.modules.gossip

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import com.risksense.ipaddr.IpAddress
import common.utils.ActorTimers.Tick
import common.utils.{ActorDefaults, ActorTimers, GrpcSettingsFactory}

import scala.collection.mutable
import scala.concurrent.duration._


object GossipActor extends GrpcSettingsFactory {

  def apply
      (membershipActor: ActorRef, delay: FiniteDuration, tag: String)
      (implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(
      Props(new GossipActor(membershipActor, delay)),
      s"gossipActor-${tag}"
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

  private val keyCounter = mutable.Map[Any, Int]()

  start(delay)


  override def receive: Receive = {

    case Tick => keyCounter.foreach {
      ???
    }

    case GossipAPI.Publish(key, count) => {

    }

    case x => log.error(receivedUnknown(x))
  }
}
