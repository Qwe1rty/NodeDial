package persistence.threading

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import common.utils.ActorDefaults
import persistence.io.IOTask

import scala.concurrent.ExecutionContext


object SingleThreadActor {

  private def props(id: Int): Props = Props(new SingleThreadActor(id))

  def apply(id: Int)(implicit parentContext: ActorContext): ActorRef =
    parentContext.actorOf(props(id), f"singleThreadActor-${id}%03d")
}


class SingleThreadActor(id: Int) extends Actor
                                 with ActorLogging
                                 with ActorDefaults {

  implicit final private val ec: ExecutionContext = SingleThreadExecutor(id)

  final private val tag = s"Thread ID ${id} -> " // TODO patternize this

  log.info(s"Thread actor created with ID: ${id}")


  override def receive: Receive = {

    case ioTask: IOTask => {
      log.debug(tag + s"IO task received by thread actor")
      ioTask.execute()
    }

    case x => log.error(receivedUnknown(x))
  }
}
