package administration.failureDetection

import administration.failureDetection.FailureDetectorConstants._
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import com.risksense.ipaddr.IpAddress
import common.administration.failureDetection._
import org.slf4j.LoggerFactory
import schema.ImplicitDataConversions._
import schema.PortConfiguration.FAILURE_DETECTOR_PORT

import scala.concurrent.{ExecutionContext, Future}


class FailureDetectorGRPCService(implicit actorSystem: ActorSystem[_]) extends FailureDetectorService {

  implicit private val executionContext: ExecutionContext = actorSystem.executionContext

  final private val log = LoggerFactory.getLogger(ClientGRPCService.getClass)
  final private val service: HttpRequest => Future[HttpResponse] = FailureDetectorServiceHandler(this)

  Http()(actorSystem.classicSystem)
    .bindAndHandleAsync(service, interface = "0.0.0.0", port = FAILURE_DETECTOR_PORT, HttpConnectionContext())
    .foreach(binding => log.info(s"Failure detector service bound to ${binding.localAddress}"))


  /**
   * RPC for initial failure check, and will be declared SUSPECT if
   * confirmation cannot be returned
   */
  override def directCheck(in: DirectMessage): Future[Confirmation] = {

    log.info("Health check request has been received, sending confirmation")
    Future.successful(Confirmation())
  }

  /**
   * RPC for followup checks, and will be declared DEAD if none of the
   * followup checks are able to be confirmed
   */
  override def followupCheck(in: FollowupMessage): Future[Confirmation] = {

    val ipAddress: IpAddress = in.ipAddress

    log.info(s"Followup check request has been received on suspect ${ipAddress}, attempting to request confirmation")
    FailureDetectorServiceClient(createGRPCSettings(ipAddress, SUSPICION_DEADLINE)(actorSystem.classicSystem))
      .directCheck(DirectMessage())
  }
}

object FailureDetectorGRPCService {

  def apply()(implicit actorSystem: ActorSystem[_]): FailureDetectorService = new FailureDetectorGRPCService()
}