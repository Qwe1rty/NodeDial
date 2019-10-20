package server

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.{ExecutionContext, Future}
import chordial.server.RequestServiceHandler

object RequestServiceManager {

  def apply(implicit actorSystem: ActorSystem): RequestServiceManager = {

    new RequestServiceManager(actorSystem)
  }
}

private class RequestServiceManager(actorSystem: ActorSystem) {

  def run(): Future[Http.ServerBinding] = {

    implicit val materializer: Materializer = ActorMaterializer()(actorSystem)
    implicit val execContext: ExecutionContext = actorSystem.dispatcher

    val service: HttpRequest => Future[HttpResponse] =
      RequestServiceHandler(new RequestServiceHandler())(materializer, actorSystem)

    Http()(actorSystem).bindAndHandleAsync(
      service,
      interface = "127.0.0.1",
      port = 8080,
      connectionContext = HttpConnectionContext())

    // TODO: log bindings: binding.foreach {...}
  }
}