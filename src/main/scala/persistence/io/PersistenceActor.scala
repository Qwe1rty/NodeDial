package persistence.io

import java.io.File
import java.nio.file.{Path, Paths}

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.stream.ActorMaterializer
import persistence.threading.SingleThreadExecutor
import server.datatypes.OperationPackage

import scala.concurrent.ExecutionContext


object PersistenceActor {

  final val DIRECTORY_NAME: Path = Paths.get("chordial/")
  final val DIRECTORY_FILE: File = DIRECTORY_NAME.toFile

  final private val PARTITION_SEED: Char = 0xAA // Hex representation of binary 10101010
  final private val PARTITION_FUNCTION: String => Int = _.foldLeft(PARTITION_SEED)(_ ^ _ toChar) toInt
}


class PersistenceActor extends Actor with ActorLogging {

  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  private val coreCount: Int = Runtime.getRuntime.availableProcessors
  private val threads: Vector[ExecutionContext] = Vector.fill(coreCount){ SingleThreadExecutor() }

  private var keyMapping = Map[String, ActorRef]()

  if (!PersistenceActor.DIRECTORY_FILE.exists()) PersistenceActor.DIRECTORY_FILE.mkdir()


  override def receive: Receive = {

    case operation: OperationPackage => {
      if (!(keyMapping isDefinedAt operation.requestHash)) {
        val thread = threads(PersistenceActor.PARTITION_FUNCTION(operation.requestHash))
        val actorProps = KeyStateActor.props(operation.requestHash, thread)
        keyMapping += operation.requestHash -> context.actorOf(actorProps)
      }
      keyMapping(operation.requestHash) ! operation.requestBody
    }

    case _ => ??? // TODO log error

  }
}