package persistence.io

import akka.actor.typed.ActorRef
import better.files.File
import persistence.io.KeyStateManager.{DeleteCompleteSignal, KeyStateAction, ReadCompleteSignal, WriteCompleteSignal}

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
    stateActor ! Right(WriteCompleteSignal(Try(valueFile.writeByteArray(value)): Try[Unit]))
  }
}

private[persistence] case class DeleteIOTask(valueFile: File)(implicit stateActor: ActorRef[KeyStateAction]) extends IOTask {

  override def execute()(implicit executionContext: ExecutionContext): Unit = {
    stateActor ! Right(DeleteCompleteSignal(Try(valueFile.delete()): Try[Unit]))
  }
}