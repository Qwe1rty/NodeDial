package service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import org.slf4j.LoggerFactory
import schema.service._

import scala.concurrent.Future


object RequestServiceImpl


class RequestServiceImpl
  (requestServiceActor: ActorRef)(implicit mat: Materializer, timeout: Timeout) extends RequestService {

  final private val log = LoggerFactory.getLogger(RequestServiceImpl.getClass)


  override def get(in: GetRequest): Future[GetResponse] = {
    log.debug(s"Get request received with key ${in.key}")
    (requestServiceActor ? in).mapTo[Future[GetResponse]].flatten
  }

  override def post(in: PostRequest): Future[PostResponse] = {
    log.debug(s"Post request received with key ${in.key}")
    (requestServiceActor ? in).mapTo[Future[PostResponse]].flatten
  }

  override def delete(in: DeleteRequest): Future[DeleteResponse] = {
    log.debug(s"Delete request received with key ${in.key}")
    (requestServiceActor ? in).mapTo[Future[DeleteResponse]].flatten
  }
}
