package server.service

import java.security.MessageDigest

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.google.protobuf.ByteString
import server.datatypes.{OperationPackage, RequestTrait}

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


class RequestServiceActor(requestProcessorActor: ActorRef) extends Actor {

  final private val hashInstance = MessageDigest.getInstance("SHA-256")


  private def hashFunction(key: String): String =
    hashInstance.digest(key.getBytes("UTF-8")).map("02x".format(_)).mkString

  override def receive: Receive = {

    case requestTrait: RequestTrait => {

      if (requestTrait.key.isEmpty)
        Future.failed(new IllegalArgumentException("Key value cannot be empty or undefined"))

      val (requestActor, future): (ActorRef, Future[_]) = requestTrait match {

        case _: GetRequest =>
          val promise = Promise[GetResponse]()
          (RequestActor(promise, RequestServiceActor.getRequestCallback, "getRequestActor"), promise.future)

        case _: PostRequest =>
          val promise = Promise[PostResponse]()
          (RequestActor(promise, RequestServiceActor.postRequestCallback, "postRequestActor"), promise.future)

        case _: DeleteRequest =>
          val promise = Promise[DeleteResponse]()
          (RequestActor(promise, RequestServiceActor.deleteRequestCallback, "deleteRequestActor"), promise.future)
      }

      requestProcessorActor ! new OperationPackage(requestActor, hashFunction(requestTrait.key), requestTrait)
      sender ! future
    }

    case _ => ??? // TODO: add error logging/handling
  }
}
