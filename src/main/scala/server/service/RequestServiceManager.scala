package server.service

import java.security.MessageDigest
import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.pattern.ask
import akka.protobuf.Descriptors.FieldDescriptor
import akka.protobuf.GeneratedMessage
import akka.stream.Materializer
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.Duration

object RequestServiceManager {

  final implicit val DEFAULT_TIMEOUT: Timeout = Timeout(Duration(5, TimeUnit.MILLISECONDS))
}

class RequestServiceManager(persistenceActor: ActorRef)(implicit mat: Materializer, timeout: Timeout) extends RequestService {

  final private val hashInstance = MessageDigest.getInstance("SHA-256")

  private def hashRequestKey(request: scalapb.GeneratedMessage): Unit = {
    request.getFieldByNumber(0)
  }

  private def hashFunction(key: String): String = {
    hashInstance.digest(key.getBytes("UTF-8")).map("02x".format(_)).mkString
  }

  override def get(in: GetRequest): Future[GetResponse] = {
    Future.successful(null)
//    (persistenceActor ? GetRequest).mapTo[GetResponse]
  }

  override def post(in: PostRequest): Future[PostResponse] = {
    Future.successful(null)
//    (persistenceActor ? PostRequest).mapTo[PostResponse]
  }

  override def delete(in: DeleteRequest): Future[DeleteResponse] = {
    Future.successful(null)
//    (persistenceActor ? DeleteRequest).mapTo[DeleteResponse]
  }
}
