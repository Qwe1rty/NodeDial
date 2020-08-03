package persistence.io

import akka.actor.ActorRef
import better.files.File

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}


sealed trait IOTask {

  def execute()(implicit executionContext: ExecutionContext): Unit
}


case class ReadTask(valueFile: File)(implicit stateActor: ActorRef) extends IOTask {

  override def execute()(implicit executionContext: ExecutionContext): Unit = {

    stateActor ! ReadCompleteSignal(Try(valueFile.loadBytes))
  }
}

case class WriteTask(valueFile: File, value: Array[Byte])(implicit stateActor: ActorRef) extends IOTask {

  override def execute()(implicit executionContext: ExecutionContext): Unit = {

    stateActor ! WriteCompleteSignal(Try(valueFile.writeByteArray(value)) match {
      case Success(_) => Success[Unit]()
      case Failure(e) => Failure(e)
    })
  }
}

case class TombstoneTask(valueFile: File)(implicit stateActor: ActorRef) extends IOTask {

  override def execute()(implicit executionContext: ExecutionContext): Unit = {

    ??? // TODO figure best way to tombstone
  }
}