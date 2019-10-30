package server.service

import akka.actor.{Actor, ActorRef, Props}

import scala.concurrent.Promise

class RequestServiceActor(requestProcessorActor: ActorRef) extends Actor {

  implicit val processorActor: ActorRef = requestProcessorActor

  override def receive: Receive = {
    case request: RequestTrait => {
      val promise: Promise[ResponseTrait] = request match {
        case _: GetRequest => Promise[GetResponse]()
        case _: PostRequest => Promise[PostResponse]()
        case _: DeleteRequest => Promise[DeleteResponse]()
        case _ => throw new Exception(":(") // TODO
      }
      // TODO make the promise types, and spawn new actor
      context.actorOf(Props())
    }
    case _ => true // TODO: add error logging/handling
  }
}
