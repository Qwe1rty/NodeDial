package service

import akka.actor.{ActorContext, ActorLogging, ActorRef, Props}
import common.utils.DefaultActor
import io.jvm.uuid._
import schema.ResponseTrait
import service.RequestActor.{RequestCallback, ResultType}

import scala.concurrent.Promise
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


object RequestActor {

  type RequestCallback[A <: ResponseTrait] = Function[Option[Array[Byte]], A]
  type ResultType = Try[Option[Array[Byte]]]


  def apply[A <: ResponseTrait: ClassTag](
      requestPromise: Promise[A],
      callback: RequestCallback[A]
    )
    (implicit parentContext: ActorContext): ActorRef = {

    parentContext.actorOf(
      Props(new RequestActor[A](requestPromise, callback)),
      s"requestActor-${UUID.random}"
    )
  }
}


class RequestActor[A <: ResponseTrait: ClassTag] private(
    requestPromise: Promise[A],
    callback: RequestCallback[A]
  )
  extends DefaultActor
  with ActorLogging {

  // NOTE: objects/type classes + actor concurrency is a bad idea, so a callback is used instead
  //  https://docs.scala-lang.org/overviews/reflection/thread-safety.html

  override def receive: Receive = {

    case ioResult: ResultType =>
      ioResult match {
        case Success(result) => requestPromise.complete(Try(callback(result)))
        case Failure(e) => requestPromise.failure(e)
      }
      context.stop(self)

    case x =>
      log.error(receivedUnknown(x))
      context.stop(self)
  }
}
