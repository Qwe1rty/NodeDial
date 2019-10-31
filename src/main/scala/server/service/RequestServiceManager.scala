package server.service

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import com.google.protobuf.ByteString

import scala.concurrent.Future
import scala.concurrent.duration.Duration

object RequestServiceManager {

  implicit final val DEFAULT_TIMEOUT: Timeout = Timeout(Duration(5, TimeUnit.MILLISECONDS))
}

class RequestServiceManager
  (requestServiceActor: ActorRef)(implicit mat: Materializer, timeout: Timeout) extends RequestService {

  override def get(in: GetRequest): Future[GetResponse] = {
    (requestServiceActor ? GetRequest).mapTo[GetResponse]
  }

  override def post(in: PostRequest): Future[PostResponse] = {
    (requestServiceActor ? PostRequest).mapTo[PostResponse]
  }

  override def delete(in: DeleteRequest): Future[DeleteResponse] = {
    (requestServiceActor ? DeleteRequest).mapTo[DeleteResponse]
  }
}
