package server.service

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.Duration


object RequestServiceManager {

  implicit final val DEFAULT_TIMEOUT: Timeout = Timeout(Duration(5, TimeUnit.MILLISECONDS))
}


class RequestServiceManager
  (requestServiceActor: ActorRef)(implicit mat: Materializer, timeout: Timeout) extends RequestService {

  final private val log = LoggerFactory.getLogger(RequestServiceManager.getClass)


  override def get(in: GetRequest): Future[GetResponse] = {
    log.debug(s"Get request received with key ${in.key}")
    (requestServiceActor ? GetRequest).mapTo[GetResponse]
  }

  override def post(in: PostRequest): Future[PostResponse] = {
    log.debug(s"Post request received with key ${in.key}")
    (requestServiceActor ? PostRequest).mapTo[PostResponse]
  }

  override def delete(in: DeleteRequest): Future[DeleteResponse] = {
    log.debug(s"Delete request received with key ${in.key}")
    (requestServiceActor ? DeleteRequest).mapTo[DeleteResponse]
  }
}
