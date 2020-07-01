package replication

import akka.actor.{ActorRef, ActorSystem, Props}
import com.roundeights.hasher.Implicits._
import replication.eventlog.Compression
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
  extends RaftActor
  with Compression {

  override type LogEntryType = LogEntry

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

  override def commit: Function[LogEntry, Unit] = ???
}
