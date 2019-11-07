package persistence

import java.nio.file.{Path, Paths}

import akka.actor.ActorRef
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Sink}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


sealed trait IORequest {

  final val WRITE_AHEAD_EXTENSION = Paths.get(".wal")
  final val VALUE_EXTENSION = Paths.get(".val")

  def schedule
      (stateActor: ActorRef, requestActor: ActorRef)
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit
}


case class ReadRequestTask(path: Path) extends IORequest {

  override def schedule
      (stateActor: ActorRef, requestActor: ActorRef)
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit = {

    FileIO.fromPath(path.resolve(VALUE_EXTENSION)).to(Sink.ignore).run onComplete {
      case Success(ioResult: IOResult) => {
        stateActor ! ReadCommittedSignal
        requestActor ! ioResult
      }
      case Failure(exception: Exception) => {
        // TODO log error
      }
    }
  }
}

case class WriteAheadTask(key: String, value: ByteString) extends IORequest {
  override def run(): Unit = ???
}

case class WriteTransferTask() extends IORequest {
  override def run(): Unit = ???
}

case class TombstoneRequestTask(key: String) extends IORequest {
  override def run(): Unit = ???
}