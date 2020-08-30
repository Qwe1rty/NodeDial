package persistence.io

import akka.actor.typed.ActorRef
import better.files.File
import persistence.io.KeyStateActor.{IOSignal, ReadCompleteSignal, WriteCompleteSignal}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}


private[persistence] sealed trait IOTask {

  def execute()(implicit executionContext: ExecutionContext): Unit
}

private[persistence] case class ReadTask(valueFile: File)(implicit stateActor: ActorRef[IOSignal]) extends IOTask {

  override def execute()(implicit executionContext: ExecutionContext): Unit = {
    stateActor ! ReadCompleteSignal(Try(valueFile.loadBytes))
  }
}

private[persistence] case class WriteTask(valueFile: File, value: Array[Byte])(implicit stateActor: ActorRef[IOSignal]) extends IOTask {

  override def execute()(implicit executionContext: ExecutionContext): Unit = {
    stateActor ! WriteCompleteSignal(Try(valueFile.writeByteArray(value)) match {
      case Success(_) => Success[Unit]()
      case Failure(e) => Failure(e)
    })
  }
}

private[persistence] case class TombstoneTask(valueFile: File)(implicit stateActor: ActorRef[IOSignal]) extends IOTask {

  override def execute()(implicit executionContext: ExecutionContext): Unit = {
    ??? // TODO figure best way to tombstone
  }
}