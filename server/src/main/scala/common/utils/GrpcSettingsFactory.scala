package common.utils

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import com.risksense.ipaddr.IpAddress

import scala.concurrent.duration.FiniteDuration


trait GrpcSettingsFactory {

  def createGrpcSettings
      (ipAddress: IpAddress, timeout: FiniteDuration)
      (implicit actorSystem: ActorSystem): GrpcClientSettings = ???
}
