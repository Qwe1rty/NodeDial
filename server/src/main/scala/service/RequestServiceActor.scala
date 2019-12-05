package service

import java.security.MessageDigest

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.google.protobuf.ByteString
import common.utils.ActorDefaults
import schema.service._
import schema.RequestTrait

import scala.concurrent.{Future, Promise}


object RequestServiceActor {

  private val getRequestCallback: Option[Array[Byte]] => GetResponse = {
    case Some(bytes) => new GetResponse(ByteString.copyFrom(bytes))
    case None => throw new IllegalStateException("Get request should not receive None object")
  }

  private val postRequestCallback: Option[Array[Byte]] => PostResponse = {
    case Some(_) => throw new IllegalStateException("Post request should not receive Some object")
    case None => new PostResponse
  }

  private val deleteRequestCallback: Option[Array[Byte]] => DeleteResponse = {
    case Some(_) => throw new IllegalStateException("Delete request should not receive Some object")
    case None => new DeleteResponse
  }

  def apply(requestProcessorActor: ActorRef)(implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(props(requestProcessorActor), "requestServiceActor")

  def props(requestProcessorActor: ActorRef): Props =
    Props(new RequestServiceActor(requestProcessorActor))
}


class RequestServiceActor(requestProcessorActor: ActorRef) extends Actor with ActorLogging with ActorDefaults {

  final private var requestCounter = Map[String, Int]()

  log.info(s"Request service actor initialized")


  private def hashKey(request: RequestTrait): String = {
    MessageDigest
      .getInstance("SHA-256")
      .digest(request.key.getBytes("UTF-8"))
      .map("%02x".format(_))
      .mkString
  }

  override def receive: Receive = {

    case request: RequestTrait => {

      if (request.key.isEmpty) {
        log.warning("Request key was empty or undefined")
        Future.failed(new IllegalArgumentException("Key value cannot be empty or undefined"))
      }

      val hash = hashKey(request)
      val requestCount = requestCounter.getOrElse(hash, 0)
      val requestActorID = s"${hash}:${requestCount}"

      val (requestActor, future): (ActorRef, Future[_]) = request match {

        case _: GetRequest =>
          log.info(s"Get request with ID '${requestActorID}' received")
          val actorName = s"getRequestActor-${requestActorID}"
          val promise = Promise[GetResponse]()
          (RequestActor(promise, RequestServiceActor.getRequestCallback, actorName), promise.future)

        case _: PostRequest =>
          log.info(s"Post request with key '${requestActorID}' received")
          val actorName = s"postRequestActor-${requestActorID}"
          val promise = Promise[PostResponse]()
          (RequestActor(promise, RequestServiceActor.postRequestCallback, actorName), promise.future)

        case _: DeleteRequest =>
          log.info(s"Delete request with key '${requestActorID}' received")
          val actorName = s"deleteRequestActor-${requestActorID}"
          val promise = Promise[DeleteResponse]()
          (RequestActor(promise, RequestServiceActor.deleteRequestCallback, actorName), promise.future)
      }

      requestCounter = requestCounter.updated(hash, requestCount + 1)
      log.debug(s"Request actor key '${request.key}' count incremented from ${requestCount} to ${requestCount + 1}")

      requestProcessorActor ! new OperationPackage(requestActor, hash, request)
      log.debug(s"Operation package sent with hash ${hash}, for key '${request.key}'")

      sender ! future
      log.debug("Future has been returned to gRPC service")
    }

    case x => log.error(receivedUnknown(x))
  }
}
