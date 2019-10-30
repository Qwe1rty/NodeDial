package server.service

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.stream.IOResult

import scala.concurrent.Promise

class RequestActor(requestPromise: Promise[ResponseTrait])(implicit requestProcessorActor: ActorRef) extends Actor with ActorLogging {

  override def receive: Receive = {
    case IOResult => ??? // TODO
    case _ => throw new Exception(":(") // TODO
  }
}
