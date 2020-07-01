package replication

import akka.actor.{ActorPath, ActorRef, ActorSystem, Props}
import com.roundeights.hasher.Implicits._
import io.jvm.uuid._
import persistence.{DeleteTask, GetTask, PersistenceTask, PostTask}
import replication.eventlog.Compression
import schema.ImplicitGrpcConversions._
import schema.service.Request
import service.OperationPackage

import scala.util.{Failure, Success, Try}


object ReplicationActor {

  def apply
      (persistenceActor: ActorRef)
      (implicit actorSystem: ActorSystem): ActorRef = {

    actorSystem.actorOf(
      Props(new ReplicationActor(persistenceActor)),
      "replicationActor"
    )
  }

  private def logEntry(key: String, command: LogCommand): LogEntry =
    new LogEntry(key.sha256.bytes, command)
}


class ReplicationActor(persistenceActor: ActorRef)(implicit actorSystem: ActorSystem)
  extends RaftActor
  with Compression {

  import ReplicationActor._

  private var pendingRequestActors = Map[UUID, ActorPath]()


  override def receive: Receive = {

    // Handles upstream requests
    case OperationPackage(requestActor, uuid, operation) => operation match {

      // Get requests do not need to go through raft, so it directly goes to the persistence layer
      case Request.GetRequest(key) =>
        persistenceActor ! GetTask(Some(requestActor), key.sha256)

      // Post requests must be committed by the raft group before it can be written to disk
      case Request.PostRequest(key, value) =>

        log.debug(s"Post request received with UUID ${uuid.string}")
        pendingRequestActors += uuid -> requestActor

        compress(value) match {
          case Success(gzip) =>
            super.receive(new AppendEntryEvent(logEntry(key, LogCommand.WRITE), uuid, gzip))
          case Failure(e) =>
            log.error(s"Compression error for key $key: ${e.getLocalizedMessage}")
        }

      // Delete requests also have to go through raft
      case Request.DeleteRequest(key) =>

        log.debug(s"Delete request received with UUID ${uuid.string}")
        pendingRequestActors += uuid -> requestActor

        super.receive(new AppendEntryEvent(logEntry(key, LogCommand.DELETE), uuid, Array[Byte]()))
    }

    // Catches raft events, along with anything else
    case x => super.receive(x)
  }

  override def commit: Commit = {

    // Process log entry, decompress it and send to persistence layer
    case AppendEntryEvent(LogEntry(keyHash, command), uuid, compressedValue) =>
      log.info(s"${command.name} entry with key hash $keyHash will now attempt to be committed")

      val requestActor = pendingRequestActors.get(uuid)
      val persistenceTask: Try[PersistenceTask] = command match {

        case LogCommand.DELETE => Success(DeleteTask(requestActor, keyHash))
        case LogCommand.WRITE => decompress(compressedValue) match {
          case Success(decompressedValue) => Success(PostTask(requestActor, keyHash, decompressedValue))
          case Failure(e) =>
            log.error(s"Decompression error for key hash $keyHash for reason: ${e.getLocalizedMessage}")
            Failure(e)
        }
      }

      persistenceTask match {
        case Success(task) => persistenceActor ! task
        case Failure(e) =>
          log.error(s"Failed to commit entry due to error: ${e.getLocalizedMessage}")
      }
  }
}
