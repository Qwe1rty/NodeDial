package persistence.io

import java.nio.file.{Path, Paths}

import akka.actor.ActorRef
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString

import scala.concurrent.ExecutionContext


sealed trait IOTask {

  final val WRITE_AHEAD_EXTENSION = Paths.get(".wal")
  final val VALUE_EXTENSION = Paths.get(".val")


  def schedule(implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit
}


case class ReadTaskTask(stateActor: ActorRef, path: Path) extends IOTask {

  override def schedule
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit = {

    FileIO.fromPath(path.resolve(VALUE_EXTENSION))
      .to(Sink.ignore).run onComplete {

      stateActor ! ReadCommittedSignal(_)
    }
  }
}

case class WriteAheadTask(stateActor: ActorRef, path: Path, value: ByteString) extends IOTask {

  override def schedule
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit = {

    Source.single(value)
      .runWith(FileIO.toPath(path.resolve(WRITE_AHEAD_EXTENSION))) onComplete {

      stateActor ! WriteAheadCommittedSignal(_)
    }
  }
}

case class WriteTransferTask(stateActor: ActorRef, path: Path) extends IOTask {

  override def schedule
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit = {

    FileIO.fromPath(path.resolve(WRITE_AHEAD_EXTENSION))
      .to(FileIO.toPath(path.resolve(VALUE_EXTENSION))).run onComplete {

      stateActor ! WriteTransferCommittedSignal(_)
    }
  }
}

case class TombstoneTaskTask(stateActor: ActorRef, path: Path) extends IOTask {

  override def schedule
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit = {

    ??? // TODO figure best way to tombstone
  }
}