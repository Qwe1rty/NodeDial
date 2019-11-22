package server.service

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import server.datatypes.ResponseTrait

import scala.concurrent.Promise
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


object RequestActor {

  def apply[A <: ResponseTrait]
      (requestPromise: Promise[A], ioProcessCallback: Option[Array[Byte]] => A, name: String)
      (implicit ct: ClassTag[A], parentContext: ActorContext): ActorRef = {

    parentContext.actorOf(props(requestPromise, ioProcessCallback), name)
  }

  def props[A <: ResponseTrait]
      (requestPromise: Promise[A], ioProcessCallback: Option[Array[Byte]] => A)
      (implicit ct: ClassTag[A]): Props = {

    Props(new RequestActor[A](requestPromise, ioProcessCallback))
  }
}


class RequestActor[+A <: ResponseTrait]
    (requestPromise: Promise[A], ioProcessCallback: Option[Array[Byte]] => A)
    (implicit ct: ClassTag[A])
  extends Actor with ActorLogging {

  // NOTE: objects/type classes + actors is a bad idea, so ioProcessCallback is used to fulfill that functionality
  //  https://docs.scala-lang.org/overviews/reflection/thread-safety.html

  override def receive: Receive = {

    case ioResult: Try[Option[Array[Byte]]] =>
      ioResult match {
        case Success(result) => requestPromise.complete(Try(ioProcessCallback(result)))
        case Failure(e) => requestPromise.failure(e)
      }
      // TODO destroy actor

    case _ => throw new Exception(":(") // TODO
  }
}
