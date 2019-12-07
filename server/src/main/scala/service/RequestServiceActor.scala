package service

import java.security.MessageDigest

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.google.protobuf.ByteString
import com.roundeights.hasher.Implicits._
import common.utils.ActorDefaults
import schema.service._
import schema.RequestTrait

import scala.concurrent.{Future, Promise}


object RequestServiceActor {


  private def props(requestProcessorActor: ActorRef): Props =
    Props(new RequestServiceActor(requestProcessorActor))

  def apply(requestProcessorActor: ActorRef)(implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(props(requestProcessorActor), "requestServiceActor")
}


class RequestServiceActor(requestProcessorActor: ActorRef) extends Actor with ActorLogging with ActorDefaults {

  final private var requestCounter = Map[String, Int]()

  log.info(s"Request service actor initialized")


  override def receive: Receive = {

    case request: RequestTrait => {

      if (request.key.isEmpty) {
        log.warning("Request key was empty or undefined")
        Future.failed(new IllegalArgumentException("Key value cannot be empty or undefined"))
      }

      val hash: String = request.key.sha256
      val requestCount = requestCounter.getOrElse(hash, 0)
      val requestActorID = s"${hash}:${requestCount}"

      val (future, requestActor): (Future[_], ActorRef) = request match {

        case _: GetRequest =>
          log.info(s"Get request with ID '${requestActorID}' received")
          val actorName = s"getRequestActor-${requestActorID}"
          val promise = Promise[GetResponse]()

          (promise.future, RequestActor(promise, actorName) {
            case Some(bytes) => new GetResponse(ByteString.copyFrom(bytes))
            case None => throw new IllegalStateException("Get request should not receive None object")
          })

        case _: PostRequest =>
          log.info(s"Post request with key '${requestActorID}' received")
          val actorName = s"postRequestActor-${requestActorID}"
          val promise = Promise[PostResponse]()

          (promise.future, RequestActor(promise, actorName) {
            case Some(_) => throw new IllegalStateException("Post request should not receive Some object")
            case None => new PostResponse
          })

        case _: DeleteRequest =>
          log.info(s"Delete request with key '${requestActorID}' received")
          val actorName = s"deleteRequestActor-${requestActorID}"
          val promise = Promise[DeleteResponse]()

          (promise.future, RequestActor(promise, actorName) {
            case Some(_) => throw new IllegalStateException("Delete request should not receive Some object")
            case None => new DeleteResponse
          })
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
