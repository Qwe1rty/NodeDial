package service

import akka.actor.{ActorContext, ActorLogging, ActorPath, ActorRef, Props}
import common.DefaultActor
import io.jvm.uuid._
import schema.ResponseTrait
import service.RequestActor.{Result, ResultCallback}

import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


object RequestActor {

  type ResultCallback[A <: ResponseTrait] = Function[PersistenceData, A]

  type Result = Try[PersistenceData]


  def apply[A <: ResponseTrait: ClassTag](
      requestPromise: Promise[A],
      callback: ResultCallback[A],
      uuid: UUID
    )
    (implicit parentContext: ActorContext): ActorRef = {

    parentContext.actorOf(
      Props(new RequestActor[A](requestPromise, callback)),
      s"requestActor-${uuid.string}"
    )
  }

  def register[A <: ResponseTrait: ClassTag](
      callback: ResultCallback[A]
    )
    (implicit parentContext: ActorContext): (ActorPath, Future[A], UUID) = {

    val requestPromise = Promise[A]()
    val uuid = UUID.random

    (apply(requestPromise, callback, uuid).path, requestPromise.future, uuid)
  }
}


class RequestActor[A <: ResponseTrait: ClassTag] private(
    requestPromise: Promise[A],
    callback: ResultCallback[A]
  )
  extends DefaultActor
  with ActorLogging {

  // NOTE: objects/type classes + actor concurrency is a bad idea, so a callback is used instead
  //  https://docs.scala-lang.org/overviews/reflection/thread-safety.html

  override def receive: Receive = {

    case ioResult: Result =>
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
