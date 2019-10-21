package server.service

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.Duration

object RequestServiceManager {

  final implicit val DEFAULT_TIMEOUT: Timeout = Timeout(Duration(5, TimeUnit.MILLISECONDS))
}

class RequestServiceManager(persistenceActor: ActorRef)(implicit mat: Materializer, timeout: Timeout) extends RequestService {

  override def get(in: GetRequest): Future[GetResponse] = {
    (persistenceActor ? GetRequest).mapTo[GetResponse]
  }

  override def post(in: PostRequest): Future[PostResponse] = {
    (persistenceActor ? PostRequest).mapTo[PostResponse]
  }

  override def delete(in: DeleteRequest): Future[DeleteResponse] = {
    (persistenceActor ? DeleteRequest).mapTo[DeleteResponse]
  }
}
