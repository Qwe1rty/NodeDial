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


  def schedule()(implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit
}


case class ReadTask(path: Path)(implicit stateActor: ActorRef) extends IOTask {

  override def schedule()
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit = {

    FileIO.fromPath(path.resolve(VALUE_EXTENSION))
      .to(Sink.ignore).run onComplete {

      stateActor ! ReadCommitSignal(_)
    }
  }
}

case class WriteAheadTask(path: Path, value: ByteString)(implicit stateActor: ActorRef) extends IOTask {

  override def schedule()
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit = {

    Source.single(value)
      .runWith(FileIO.toPath(path.resolve(WRITE_AHEAD_EXTENSION))) onComplete {

      stateActor ! WriteAheadCommitSignal(_)
    }
  }
}

case class WriteTransferTask(path: Path)(implicit stateActor: ActorRef) extends IOTask {

  override def schedule()
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit = {

    FileIO.fromPath(path.resolve(WRITE_AHEAD_EXTENSION))
      .to(FileIO.toPath(path.resolve(VALUE_EXTENSION))).run onComplete {

      stateActor ! WriteTransferCommitSignal(_)
    }
  }
}

case class TombstoneTask(path: Path)(implicit stateActor: ActorRef) extends IOTask {

  override def schedule()
      (implicit materializer: ActorMaterializer, executionContext: ExecutionContext): Unit = {

    ??? // TODO figure best way to tombstone
  }
}