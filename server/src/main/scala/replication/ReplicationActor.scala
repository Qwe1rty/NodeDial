package replication

import akka.actor.{ActorPath, ActorRef, ActorSystem, Props}
import com.roundeights.hasher.Implicits._
import io.jvm.uuid._
import persistence.{DeleteTask, GetTask, PostTask}
import replication.ReplicatedOp.OperationType
import replication.eventlog.{Compression, ProtobufSerializer}
import schema.ImplicitGrpcConversions._
import schema.service.Request
import service.OperationPackage

import scala.util.{Failure, Success}


object ReplicationActor {

  def apply
      (persistenceActor: ActorRef)
      (implicit actorSystem: ActorSystem): ActorRef = {

    actorSystem.actorOf(
      Props(new ReplicationActor(persistenceActor)),
      "replicationActor"
    )
  }
}


class ReplicationActor(persistenceActor: ActorRef)(implicit actorSystem: ActorSystem)
  extends RaftActor[ReplicatedOp]
  with ProtobufSerializer[ReplicatedOp]
  with Compression {

  private var pendingRequestActors = Map[UUID, ActorPath]()


  /**
   * Receives messages from both the external gRPC replication/raft instance, as well as upstream
   * CRUD requests
   */
  override def receive: Receive = {

    // Handles upstream requests
    case OperationPackage(requestActor, uuid, operation) => operation match {

      case Request.GetRequest(key) =>

        // Get requests do not need to go through raft, so it directly goes to the persistence layer
        log.debug(s"Get request received with UUID ${uuid.string}")
        persistenceActor ! GetTask(Some(requestActor), key.sha256)

      case Request.PostRequest(key, value) =>

        // Post requests must be committed by the raft group before it can be written to disk
        log.debug(s"Post request received with UUID ${uuid.string}")
        pendingRequestActors += uuid -> requestActor

        // TODO make a compressed version of the new log entry type

        compressBytes(value) match {
          case Success(gzip) => super.receive(AppendEntryEvent(LogEntry(key, ???), Some(uuid)))
          case Failure(e) => log.error(s"Compression error for key $key: ${e.getLocalizedMessage}")
        }

      case Request.DeleteRequest(key) =>

        // Delete requests also have to go through raft
        log.debug(s"Delete request received with UUID ${uuid.string}")
        pendingRequestActors += uuid -> requestActor

        super.receive(AppendEntryEvent(LogEntry(key, Array[Byte]()), Some(uuid)))
    }

    case x => super.receive(x) // Catches raft events, along with anything else
  }

  /**
   * The commit function is called after the Raft process has determined a majority of the
   * servers have agreed to append the log entry, and now needs to be interpreted by the
   * user code
   */
  override def commit: Commit = { case ReplicatedOp(operation) => operation match {

    case OperationType.Read(ReadOp(key, uuid)) =>
      log.info(s"Get entry with key $key and UUID ${uuid: String} has been received as Raft commit")

      val requestActor = pendingRequestActors.get(uuid)
      persistenceActor ! GetTask(requestActor, byteStringToString(key).sha256)

    case OperationType.Write(WriteOp(key, compressedValue, uuid)) =>
      log.info(s"Write entry with key $key and UUID ${uuid: String} will now attempt to be committed")

      val requestActor = pendingRequestActors.get(uuid)
      decompressBytes(compressedValue) match {
        case Success(value) => persistenceActor ! PostTask(requestActor, byteStringToString(key).sha256, value)
        case Failure(e) => log.error(s"Decompression error for key $key for reason: ${e.getLocalizedMessage}")
      }

    case OperationType.Delete(DeleteOp(key, uuid)) =>
      log.info(s"Delete entry with key $key and UUID ${uuid: String} will now attempt to be committed")

      val requestActor = pendingRequestActors.get(uuid)
      persistenceActor ! DeleteTask(requestActor, byteStringToString(key).sha256)

    case OperationType.Empty =>
      log.error("Received empty replicated operation type!")
  }}

}
