package persistence.threading

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import common.ActorDefaults
import persistence.io.IOTask

import scala.concurrent.ExecutionContext


object SingleThreadActor {

  def apply()(implicit parentContext: ActorContext): ActorRef =
    parentContext.actorOf(props, "singleThreadActor")

  def props: Props = Props(new SingleThreadActor)
}


class SingleThreadActor() extends Actor with ActorLogging with ActorDefaults {

  implicit final private val ec: ExecutionContext = SingleThreadExecutor()


  override def receive: Receive = {

    case ioTask: IOTask => ioTask.execute()
    case x => log.error(unknownTypeMessage(x))
  }
}
