package persistence.threading

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import common.ActorDefaults
import persistence.io.IOTask

import scala.concurrent.ExecutionContext


object SingleThreadActor {

  def apply(id: Integer)(implicit parentContext: ActorContext): ActorRef =
    parentContext.actorOf(props(id), "singleThreadActor")

  def props(id: Integer): Props = Props(new SingleThreadActor(id))
}


class SingleThreadActor(id: Integer) extends Actor with ActorLogging with ActorDefaults {

  implicit final private val ec: ExecutionContext = SingleThreadExecutor(id)

  final private val tag = s"${id} -> " // TODO patternize this

  log.info(s"Thread actor created with ID: ${id}")


  override def receive: Receive = {

    case ioTask: IOTask => {
      log.debug(tag + s"IO task received by thread actor")
      ioTask.execute()
    }

    case x => log.error(receivedUnknown(x))
  }
}
