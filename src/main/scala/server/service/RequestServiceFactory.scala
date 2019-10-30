package server.service

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

object RequestServiceFactory {

  def apply(requestProcessorActor: ActorRef)(implicit actorSystem: ActorSystem): RequestServiceFactory = {

    new RequestServiceFactory(requestProcessorActor)
  }
}

class RequestServiceFactory(requestProcessorActor: ActorRef)(implicit actorSystem: ActorSystem) {

  def run(): Future[Http.ServerBinding] = {

    implicit val system: ActorSystem = actorSystem
    implicit val materializer: Materializer = ActorMaterializer()
    implicit val execContext: ExecutionContext = actorSystem.dispatcher
    implicit val timeout: Timeout = RequestServiceManager.DEFAULT_TIMEOUT

    val requestServiceActor = actorSystem.actorOf(
      Props(classOf[RequestServiceActor], requestProcessorActor),
      "requestServiceActor")

    val service: HttpRequest => Future[HttpResponse] =
      RequestServiceHandler(new RequestServiceManager(requestServiceActor, requestProcessorActor))

    Http()(actorSystem).bindAndHandleAsync(
      service,
      interface = "127.0.0.1",
      port = 8080,
      connectionContext = HttpConnectionContext())

    // TODO: log bindings: binding.foreach {...}
  }
}