package membership.failureDetection

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.stream.ActorMaterializer
import com.risksense.ipaddr.IpAddress
import common.ChordialConstants
import common.membership.failureDetection._
import membership.failureDetection.FailureDetectorConstants._
import org.slf4j.LoggerFactory
import schema.ImplicitDataConversions._
import service.RequestServiceImpl

import scala.concurrent.{ExecutionContext, Future}


object FailureDetectorServiceImpl {

  def apply()(implicit actorSystem: ActorSystem): FailureDetectorService =
    new FailureDetectorServiceImpl()
}


class FailureDetectorServiceImpl(implicit actorSystem: ActorSystem) extends FailureDetectorService {

  implicit private val materializer: ActorMaterializer = ActorMaterializer()
  implicit private val executionContext: ExecutionContext = actorSystem.dispatcher

  final private val log = LoggerFactory.getLogger(RequestServiceImpl.getClass)
  final private val service: HttpRequest => Future[HttpResponse] = FailureDetectorServiceHandler(this)

  Http()
    .bindAndHandleAsync(
      service,
      interface = "0.0.0.0",
      port = ChordialConstants.MEMBERSHIP_PORT,
      connectionContext = HttpConnectionContext())
    .foreach(
      binding => log.info(s"Failure detector service bound to ${binding.localAddress}")
    )


  /**
   * RPC for initial failure check, and will be declared SUSPECT if
   * confirmation cannot be returned
   */
  override def directCheck(in: DirectMessage): Future[Confirmation] = {
    log.info("Health check request has been received, attempting to send confirmation")
    Future.successful(Confirmation())
  }

  /**
   * RPC for followup checks, and will be declared DEAD if none of the
   * followup checks are able to be confirmed
   */
  override def followupCheck(in: FollowupMessage): Future[Confirmation] = {

    val ipAddress: IpAddress = in.ipAddress

    log.info(s"Followup check request has been received on suspect ${ipAddress}, attempting to request confirmation")
    FailureDetectorServiceClient(createGrpcSettings(ipAddress, SUSPICION_DEADLINE))
      .directCheck(DirectMessage())
  }
}
