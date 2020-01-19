package membership.failureDetection

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import com.risksense.ipaddr.IpAddress
import common.utils.GrpcSettingsFactory
import schema.ImplicitDataConversions._
import schema.PortConfiguration.MEMBERSHIP_PORT

import scala.concurrent.duration._


private[failureDetection] object FailureDetectorConstants extends GrpcSettingsFactory {

  val SUSPICION_DEADLINE: FiniteDuration = 20.second
  val DEATH_DEADLINE: FiniteDuration = 45.second

  val DIRECT_CONNECTIONS_LIMIT: Int = 5
  val FOLLOWUP_TEAM_SIZE: Int = 3


  override def createGrpcSettings
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
