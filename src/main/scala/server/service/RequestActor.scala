package server.service

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.IOResult

import scala.concurrent.Promise
import scala.reflect.ClassTag

object RequestActor {

  def props[A](requestPromise: Promise[A], ioProcessCallback: IOResult => A)
              (implicit requestProcessorActor: ActorRef, ct: ClassTag[A]): Props =
    Props(new RequestActor[A](requestPromise, ioProcessCallback))
}

class RequestActor[+A <: ResponseTrait]
    (requestPromise: Promise[A], ioProcessCallback: IOResult => A)
    (implicit requestProcessorActor: ActorRef, ct: ClassTag[A])
  extends Actor with ActorLogging {

  override def receive: Receive = {
    case IOResult => ??? // TODO
    case _ => throw new Exception(":(") // TODO
  }
}
