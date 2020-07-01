package service

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import com.google.protobuf.ByteString
import com.roundeights.hasher.Implicits._
import common.utils.DefaultActor
import membership.api.DeclareReadiness
import schema.RequestTrait
import schema.service.Request.{DeleteRequest, GetRequest, PostRequest}
import schema.service.Response.{DeleteResponse, GetResponse, PostResponse}

import scala.concurrent.{Future, Promise}


object RequestServiceActor {

  def apply
      (requestProcessorActor: ActorRef, membershipActor: ActorRef)
      (implicit actorSystem: ActorSystem): ActorRef = {

    actorSystem.actorOf(
      Props(new RequestServiceActor(requestProcessorActor, membershipActor)),
      "requestServiceActor"
    )
  }

}


class RequestServiceActor private(
    requestProcessorActor: ActorRef,
    membershipActor: ActorRef
  )
  extends DefaultActor
  with ActorLogging {

  final private var requestCounter = Map[String, Int]()

  membershipActor ! DeclareReadiness
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
