package persistence.io

import akka.actor.typed.ActorRef
import better.files.File
import persistence.io.KeyStateManager.{KeyStateAction, ReadCompleteSignal, WriteCompleteSignal}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}


private[persistence] sealed trait IOTask {

  def execute()(implicit executionContext: ExecutionContext): Unit
}

private[persistence] case class ReadIOTask(valueFile: File)(implicit stateActor: ActorRef[KeyStateAction]) extends IOTask {

  override def execute()(implicit executionContext: ExecutionContext): Unit = {
    stateActor ! Right(ReadCompleteSignal(
      if (valueFile.exists) Try(Some(valueFile.loadBytes)) else Success(None)
    ))
  }
}

private[persistence] case class WriteIOTask(valueFile: File, value: Array[Byte])(implicit stateActor: ActorRef[KeyStateAction]) extends IOTask {

  override def execute()(implicit executionContext: ExecutionContext): Unit = {
    stateActor ! Right(WriteCompleteSignal(Try(valueFile.writeByteArray(value)) match {
      case Success(_) => Success[Unit]()
      case Failure(e) => Failure(e)
    }))
  }
}

private[persistence] case class TombstoneIOTask(valueFile: File)(implicit stateActor: ActorRef[KeyStateAction]) extends IOTask {

  override def execute()(implicit executionContext: ExecutionContext): Unit = {
    ??? // TODO implement this
  }
}