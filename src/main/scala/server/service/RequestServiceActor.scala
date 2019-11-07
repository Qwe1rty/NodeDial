package server.service

import java.security.MessageDigest

import akka.actor.{Actor, ActorRef}
import akka.stream.IOResult
import com.google.protobuf.ByteString
import server.datatypes.{OperationPackage, RequestTrait}

import scala.concurrent.{Future, Promise}


class RequestServiceActor(implicit requestProcessorActor: ActorRef) extends Actor {

  final private val hashInstance = MessageDigest.getInstance("SHA-256")


  private def hashFunction(key: String): String = {
    hashInstance.digest(key.getBytes("UTF-8")).map("02x".format(_)).mkString
  }

  override def receive: Receive = {

    case requestTrait: RequestTrait => {

      if (requestTrait.key.isEmpty) Future.failed(new IllegalArgumentException("Key value cannot be empty or undefined"))
      val operationRequest = OperationPackage(hashFunction(requestTrait.key), requestTrait)

      requestTrait match {
        case _: GetRequest => {
          val promise = Promise[GetResponse]()
          context.actorOf(
            RequestActor.props[GetResponse](
              promise,
              (_: IOResult) => GetResponse(ByteString.EMPTY), // TODO replace this
              operationRequest
            ),
            "getRequestActor")
        }

        case _: PostRequest => {
          val promise = Promise[PostResponse]()
          context.actorOf(
            RequestActor.props[PostResponse](promise, (_: IOResult) => PostResponse(), operationRequest),
            "postRequestActor")
          promise.future
        }

        case _: DeleteRequest => {
          val promise = Promise[DeleteResponse]()
          context.actorOf(
            RequestActor.props[DeleteResponse](promise, (_: IOResult) => DeleteResponse(), operationRequest),
            "deleteRequestActor")
          promise.future
        }
      }

      // TODO: shutdown actor
    }

    case _ => ??? // TODO: add error logging/handling
  }
}
