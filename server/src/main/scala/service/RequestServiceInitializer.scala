package service

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.stream.{ActorMaterializer, Materializer}
import common.ChordialDefaults
import org.slf4j.LoggerFactory
import schema.service.RequestServiceHandler

import scala.concurrent.{ExecutionContext, Future}


object RequestServiceInitializer {

  def apply(requestProcessorActor: ActorRef)(implicit actorSystem: ActorSystem): RequestServiceInitializer = {

    new RequestServiceInitializer(requestProcessorActor)
  }
}


class RequestServiceInitializer(requestProcessorActor: ActorRef)(implicit actorSystem: ActorSystem) {

  final private val log = LoggerFactory.getLogger(RequestServiceActor.getClass)


  def run(): Unit = {

    import common.ChordialDefaults.EXTERNAL_REQUEST_TIMEOUT

    implicit val materializer: Materializer = ActorMaterializer()
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher

    val requestServiceActor = RequestServiceActor(requestProcessorActor)
    log.debug("Initialized request service actor")

    val service: HttpRequest => Future[HttpResponse] =
      RequestServiceHandler(new RequestServiceImpl(requestServiceActor))

    Http()
      .bindAndHandleAsync(
        service,
        interface = "127.0.0.1",
        port = ChordialDefaults.EXTERNAL_REQUEST_PORT,
        connectionContext = HttpConnectionContext())
      .foreach {
        binding => log.info(s"gRPC request service bound to ${binding.localAddress}")
      }

  }
}