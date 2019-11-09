package persistence.threading

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.ActorMaterializer
import persistence.io.IOTask

import scala.concurrent.ExecutionContext


object SingleThreadActor {

  def props(implicit materializer: ActorMaterializer): Props = Props(new SingleThreadActor)
}


class SingleThreadActor(implicit materializer: ActorMaterializer) extends Actor with ActorLogging {

  implicit final private val ec: ExecutionContext = SingleThreadExecutor()


  override def receive: Receive = {

    case ioTask: IOTask => ioTask.schedule()
    case _ => ??? // TODO log error
  }
}
