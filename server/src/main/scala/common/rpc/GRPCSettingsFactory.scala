package common.rpc

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import com.risksense.ipaddr.IpAddress

import scala.concurrent.duration.FiniteDuration


trait GRPCSettingsFactory {

  def createGRPCSettings
      (ipAddress: IpAddress, timeout: FiniteDuration)
      (implicit actorSystem: ActorSystem): GrpcClientSettings
}
