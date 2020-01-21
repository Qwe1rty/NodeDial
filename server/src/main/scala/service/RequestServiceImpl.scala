package service

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.pattern.ask
import akka.stream.{ActorMaterializer, Materializer}
import membership.MembershipAPI
import org.slf4j.LoggerFactory
import schema.PortConfiguration.EXTERNAL_REQUEST_PORT
import schema.service._

import scala.concurrent.{ExecutionContext, Future}


object RequestServiceImpl {

  def apply
      (requestServiceActor: ActorRef, membershipActor: ActorRef)
      (implicit actorSystem: ActorSystem): RequestService = {

    new RequestServiceImpl(requestServiceActor, membershipActor)
  }
}


class RequestServiceImpl
    (requestServiceActor: ActorRef, membershipActor: ActorRef)
    (implicit actorSystem: ActorSystem)
  extends RequestService {

  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  final private val log = LoggerFactory.getLogger(RequestServiceImpl.getClass)
  final private val service: HttpRequest => Future[HttpResponse] = RequestServiceHandler(this)

  Http()
    .bindAndHandleAsync(
      service,
      interface = "0.0.0.0",
      port = EXTERNAL_REQUEST_PORT,
      connectionContext = HttpConnectionContext())
    .foreach(
      binding => log.info(s"gRPC request service bound to ${binding.localAddress}")
    )

  import common.ServerDefaults.EXTERNAL_REQUEST_TIMEOUT


  override def get(in: GetRequest): Future[GetResponse] = {
    log.debug(s"Get request received with key ${in.key}")

    (requestServiceActor ? in)
      .mapTo[Future[GetResponse]]
      .flatten
  }

  override def post(in: PostRequest): Future[PostResponse] = {
    log.debug(s"Post request received with key ${in.key}")

    (requestServiceActor ? in)
      .mapTo[Future[PostResponse]]
      .flatten
  }

  override def delete(in: DeleteRequest): Future[DeleteResponse] = {
    log.debug(s"Delete request received with key ${in.key}")

    (requestServiceActor ? in)
      .mapTo[Future[DeleteResponse]]
      .flatten
  }

  override def readiness(in: ReadinessCheck): Future[ReadinessConfirmation] = {
    log.debug(s"Readiness check received")

    (membershipActor ? MembershipAPI.CheckReadiness)
      .mapTo[Boolean]
      .map { readiness =>
        log.debug(s"Readiness check response with: ${readiness}")
        readiness
      }
      .map(ReadinessConfirmation(_))
  }
}
