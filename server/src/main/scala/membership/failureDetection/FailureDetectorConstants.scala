package membership.failureDetection

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import com.risksense.ipaddr.IpAddress
import common.rpc.GRPCSettingsFactory
import schema.ImplicitDataConversions._
import schema.PortConfiguration.FAILURE_DETECTOR_PORT

import scala.concurrent.duration._


private[failureDetection] object FailureDetectorConstants extends GRPCSettingsFactory {

  val SUSPICION_DEADLINE: FiniteDuration = 20.second
  val DEATH_DEADLINE: FiniteDuration = 45.second

  val DIRECT_CONNECTIONS_LIMIT: Int = 5
  val FOLLOWUP_TEAM_SIZE: Int = 3


  override def createGRPCSettings
    (ipAddress: IpAddress, timeout: FiniteDuration)
    (implicit actorSystem: ActorSystem): GrpcClientSettings = {

    GrpcClientSettings
      .connectToServiceAt(
        ipAddress,
        FAILURE_DETECTOR_PORT
      )
      .withDeadline(timeout)
  }
}
