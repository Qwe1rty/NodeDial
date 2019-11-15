package persistence.threading

import akka.actor.{Actor, ActorLogging, Props}
import persistence.io.IOTask

import scala.concurrent.ExecutionContext


object SingleThreadActor {

  def props: Props = Props(new SingleThreadActor)
}


class SingleThreadActor() extends Actor with ActorLogging {

  implicit final private val ec: ExecutionContext = SingleThreadExecutor()


  override def receive: Receive = {

    case ioTask: IOTask => ioTask.execute()
    case _ => ??? // TODO log error
  }
}
