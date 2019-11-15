package server.service

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.IOResult
import server.datatypes.{OperationPackage, ResponseTrait}

import scala.concurrent.Promise
import scala.reflect.ClassTag
import scala.util.Try


object RequestActor {

  def props[A <: ResponseTrait]
      (requestPromise: Promise[A], ioProcessCallback: Try[Option[Array[Byte]]] => A)
      (implicit ct: ClassTag[A]): Props =
    Props(new RequestActor[A](requestPromise, ioProcessCallback))
}


class RequestActor[+A <: ResponseTrait]
    (requestPromise: Promise[A], ioProcessCallback: Try[Option[Array[Byte]]] => A)
    (implicit ct: ClassTag[A])
  extends Actor with ActorLogging {

  // NOTE: objects/type classes + actors is a bad idea, so ioProcessCallback is used to fulfill that functionality
  //  https://docs.scala-lang.org/overviews/reflection/thread-safety.html


  override def receive: Receive = {
    case ioResult: Try[Option[Array[Byte]]] => ioProcessCallback(ioResult)
    case _ => throw new Exception(":(") // TODO
  }
}
