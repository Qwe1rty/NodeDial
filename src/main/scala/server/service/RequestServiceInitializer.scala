package server.service

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}


object RequestServiceInitializer {

  def apply(requestProcessorActor: ActorRef)(implicit actorSystem: ActorSystem): RequestServiceInitializer = {

    new RequestServiceInitializer(requestProcessorActor)
  }
}


class RequestServiceInitializer(requestProcessorActor: ActorRef)(implicit actorSystem: ActorSystem) {

  def run(): Future[Http.ServerBinding] = {

    implicit val materializer: Materializer = ActorMaterializer()
    implicit val execContext: ExecutionContext = actorSystem.dispatcher
    implicit val timeout: Timeout = RequestServiceManager.DEFAULT_TIMEOUT

    val requestServiceActor = RequestServiceActor(requestProcessorActor)
    val service: HttpRequest => Future[HttpResponse] =
      RequestServiceHandler(new RequestServiceManager(requestServiceActor))

    Http()(actorSystem).bindAndHandleAsync(
      service,
      interface = "127.0.0.1",
      port = 8080,
      connectionContext = HttpConnectionContext())

    // TODO: log bindings: binding.foreach {...}
  }
}