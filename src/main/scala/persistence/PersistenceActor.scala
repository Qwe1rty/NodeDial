package persistence

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.concurrent.Executors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.stream.ActorMaterializer
import server.datatypes.OperationPackage

import scala.collection.mutable
import scala.concurrent.ExecutionContext

object PersistenceActor {

  final val DIRECTORY_NAME: Path = Paths.get("chordial/")
  final val DIRECTORY_FILE: File = DIRECTORY_NAME.toFile

  final val PARTITION_SEED: Char = 0xAA // Hex representation of binary: 10101010
  final val PARTITION_FUNCTION: String => Int = _.foldLeft(PARTITION_SEED)(_ ^ _ toChar) toInt
}

class PersistenceActor extends Actor with ActorLogging {

  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  private val keyMapping = mutable.Map[String, ActorRef]()

  private val coreCount: Int = Runtime.getRuntime.availableProcessors
  private val threads: Array[ExecutionContext] = Array.fill(coreCount){

    new ExecutionContext {
      private val threadExecutor = Executors.newFixedThreadPool(1)

      override def execute(runnable: Runnable): Unit = {
        threadExecutor.submit(runnable)
      }
      override def reportFailure(cause: Throwable): Unit = {
        // TODO log error
      }
      def shutdown(): Unit = threadExecutor.shutdown()
    }
  }

  if (!PersistenceActor.DIRECTORY_FILE.exists()) PersistenceActor.DIRECTORY_FILE.mkdir()


  override def receive: Receive = {

    case operation: OperationPackage => {

      if (!(keyMapping isDefinedAt operation.requestHash)) {
        val thread = threads(PersistenceActor.PARTITION_FUNCTION(operation.requestHash))
        keyMapping += operation.requestHash -> context.actorOf(KeyStateActor.props(thread))
      }

      keyMapping(operation.requestHash) ! operation.requestBody
    }

    case _ => ??? // TODO log error

  }
}