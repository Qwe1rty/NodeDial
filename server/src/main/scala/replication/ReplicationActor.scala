package replication

import akka.actor.{ActorRef, ActorSystem, Props}
import com.roundeights.hasher.Implicits._
import replication.eventlog.Compression
import schema.ImplicitGrpcConversions._
import schema.RequestTrait
import schema.service.Request
import schema.service.Request.{DeleteRequest, PostRequest}
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
}


class ReplicationActor(persistenceActor: ActorRef)(implicit actorSystem: ActorSystem)
  extends RaftActor
  with Compression {

  override def receive: Receive = {

    // Handles upstream requests
    case operation: OperationPackage => operation.requestBody match {

      // Get requests do not need to go through raft, so it gets read from disk immediately
      case getRequest: Request.GetRequest =>
        persistenceActor ! getRequest

      // Post requests must be committed by the raft group before it can be written to disk
      case Request.PostRequest(key, value) => compress(value) match {
        case Success(gzip) =>
          super.receive(new AppendEntryEvent(new LogEntry(key.sha256.bytes, LogCommand.WRITE), gzip))
        case Failure(e) =>
          log.error(s"Compression error for key $key for reason: ${e.getLocalizedMessage}")
      }

      // Delete requests also have to go through raft
      case Request.DeleteRequest(key) =>
        super.receive(new AppendEntryEvent(new LogEntry(key.sha256.bytes, LogCommand.DELETE), Array[Byte]()))
    }

    // Catches raft events, along with anything else
    case x => super.receive(x)
  }

  override def commit: Function[AppendEntryEvent, Unit] = {

    // Process log entry, decompress it and send to persistence layer
    case AppendEntryEvent(LogEntry(keyHash, command), compressedValue) =>
      log.info(s"${command.name} entry with key hash $keyHash will now attempt to be committed")

      val operationBody: Try[RequestTrait] = command match {
        case LogCommand.DELETE => Success(new DeleteRequest(new String()))
        case LogCommand.WRITE => decompress(compressedValue) match {
          case Success(value) => Success(new PostRequest(new String(), value))
          case failure: Failure[RequestTrait] =>
            log.error(s"Decompression error for key hash $keyHash for reason: ${failure.exception.getLocalizedMessage}")
            failure
        }
      }

      operationBody match {
        case Success(body) => persistenceActor ! new OperationPackage(???, keyHash, body)
        case Failure(e) =>
          log.error(s"Failed to commit entry due to error: ${e.getLocalizedMessage}")
      }
  }
}
