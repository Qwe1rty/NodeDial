package service

import akka.actor.{ActorLogging, ActorPath, ActorRef, ActorSystem, Props}
import com.roundeights.hasher.Implicits._
import common.utils.DefaultActor
import membership.api.DeclareReadiness
import schema.ImplicitGrpcConversions._
import schema.RequestTrait
import schema.service.Request.{DeleteRequest, GetRequest, PostRequest}
import schema.service.Response.{DeleteResponse, GetResponse, PostResponse}
import service.RequestActor.RequestCallback

import scala.concurrent.{Future, Promise}


object RequestServiceActor {

  def apply(
      requestProcessorActor: ActorRef,
      membershipActor: ActorRef
    )
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

  membershipActor ! DeclareReadiness
  log.info(s"Request service actor initialized")


  override def receive: Receive = {

    case request: RequestTrait =>

      if (request.key.isEmpty) {
        log.warning("Request key was empty or undefined")
        Future.failed(new IllegalArgumentException("Key value cannot be empty or undefined"))
      }

      val hash: String = request.key.sha256
      val (future, requestActor): (Future[_], ActorPath) = {

        val (promise, callback): (Promise[_], RequestCallback[_]) = request match {
          case _: GetRequest =>
            log.info(s"Get request with key '${request.key}' received")

            (Promise[GetResponse](), {
              case Some(bytes) => new GetResponse(bytes)
              case None => throw new IllegalStateException("Get request should not receive None object")
            }: RequestCallback[_])

          case _: PostRequest =>
            log.info(s"Post request with key '${request.key}' received")

            (Promise[PostResponse](), {
              case Some(_) => throw new IllegalStateException("Post request should not receive Some object")
              case None => new PostResponse
            })

          case _: DeleteRequest =>
            log.info(s"Delete request with key '${request.key}' received")

            (Promise[DeleteResponse](), {
              case Some(_) => throw new IllegalStateException("Delete request should not receive Some object")
              case None => new DeleteResponse
            })
        }

        (promise.future, RequestActor(promise, callback).path)
      }

      log.debug(s"Request actor initiated with path $requestActor for key '${request.key}'")

      requestProcessorActor ! new OperationPackage(requestActor, hash, request)
      log.debug(s"Operation package sent with hash ${hash}, for key '${request.key}'")

      sender ! future
      log.debug("Future has been returned to gRPC service")

    case x => log.error(receivedUnknown(x))
  }
}
