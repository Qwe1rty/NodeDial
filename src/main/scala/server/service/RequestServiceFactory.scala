package server.service

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

object RequestServiceFactory {

  def apply(persistenceActor: ActorRef)(implicit actorSystem: ActorSystem): RequestServiceFactory = {

    new RequestServiceFactory(persistenceActor)
  }
}

class RequestServiceFactory(persistenceActor: ActorRef)(implicit actorSystem: ActorSystem) {

  def run(): Future[Http.ServerBinding] = {

    implicit val materializer: Materializer = ActorMaterializer()(actorSystem)
    implicit val execContext: ExecutionContext = actorSystem.dispatcher
    implicit val timeout: Timeout = RequestServiceManager.DEFAULT_TIMEOUT

    val service: HttpRequest => Future[HttpResponse] =
      RequestServiceHandler(new RequestServiceManager(persistenceActor))(materializer, actorSystem)

    Http()(actorSystem).bindAndHandleAsync(
      service,
      interface = "127.0.0.1",
      port = 8080,
      connectionContext = HttpConnectionContext())

    // TODO: log bindings: binding.foreach {...}
  }
}