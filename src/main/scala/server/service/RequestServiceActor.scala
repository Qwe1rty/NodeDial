package server.service

import akka.actor.{Actor, ActorRef, Props}
import akka.stream.IOResult
import com.google.protobuf.ByteString

import scala.concurrent.Promise

class RequestServiceActor(requestProcessorActor: ActorRef) extends Actor {

  implicit val processorActor: ActorRef = requestProcessorActor

  override def receive: Receive = {

    case GetRequest(key) => {
      val promise = Promise[GetResponse]()
      context.actorOf(
        RequestActor.props[GetResponse](
          promise,
          (ioResult: IOResult) => GetResponse(ByteString.EMPTY)
        ),
        "getRequestActor")
    }

    case PostRequest(key, value) => {

    }

    case DeleteRequest(key) => {

    }

    case _ => true // TODO: add error logging/handling
  }
}
