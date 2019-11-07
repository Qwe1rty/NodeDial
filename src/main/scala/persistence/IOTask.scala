package persistence

import java.nio.file.{Path, Paths}

import akka.actor.ActorRef
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


sealed trait IORequest {

  final val WRITE_AHEAD_EXTENSION = Paths.get(".wal")
  final val VALUE_EXTENSION = Paths.get(".val")

  def schedule
      (stateActor: ActorRef)
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit
}


case class ReadRequestTask(path: Path) extends IORequest {

  override def schedule
      (stateActor: ActorRef)
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit = {

    FileIO.fromPath(path.resolve(VALUE_EXTENSION)).to(Sink.ignore).run onComplete {
      stateActor ! ReadCommittedSignal(_)
    }
  }
}

case class WriteAheadTask(path: Path, value: ByteString) extends IORequest {

  override def schedule
      (stateActor: ActorRef)
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit = {

    Source.single(value).runWith(FileIO.toPath(path.resolve(WRITE_AHEAD_EXTENSION))) onComplete {
      stateActor ! WriteAheadCommittedSignal(_)
    }
  }
}

case class WriteTransferTask(path: Path) extends IORequest {

  override def schedule
      (stateActor: ActorRef)
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit = {

    FileIO.fromPath(path.resolve(WRITE_AHEAD_EXTENSION))
    ???
  }
}

case class TombstoneRequestTask(path: Path) extends IORequest {

  override def schedule
      (stateActor: ActorRef)
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit = {

    ??? // TODO figure best way to tombstone
  }
}