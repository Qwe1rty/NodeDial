package replication

import administration.Administration.{AdministrationMessage, DeclareReadiness}
import administration.addresser.AddressRetriever
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import better.files.File
import com.roundeights.hasher.Implicits._
import common.ServerConstants
import common.persistence.{Compression, ProtobufSerializer}
import io.jvm.uuid._
import persistence.PersistenceComponent._
import replication.ReplicatedOp.OperationType
import replication.ReplicationComponent.ClientOperation
import scalapb.GeneratedMessageCompanion
import schema.ImplicitGrpcConversions._

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success}


class ReplicationComponent(
    override protected val context: ActorContext[ClientOperation],
    membershipActor: ActorRef[AdministrationMessage],
    persistenceActor: ActorRef[PersistenceTask],
    addressRetriever: AddressRetriever,
  )
  extends AbstractBehavior[ClientOperation](context)
  with Compression {

  import ReplicationComponent._

  implicit private val classicSystem: ActorSystem = context.system.classicSystem
  implicit private val executionContext: ExecutionContext = context.system.executionContext

  // TODO use a Raft-provided logger when in Raft context
  private val raft: Raft[ReplicatedOp] = new Raft[ReplicatedOp](addressRetriever, { commit =>

    val commitPromise = Promise[PersistenceData]()
    commit.operationType match {

      case OperationType.Read(ReadOp(key, uuid)) =>
        context.log.info(s"Get entry with key $key and UUID ${uuid: String} has been received as Raft commit")
        persistenceActor ! GetTask(commitPromise, byteStringToString(key).sha256)

      case OperationType.Write(WriteOp(key, compressedValue, uuid)) =>
        context.log.info(s"Write entry with key $key and UUID ${uuid: String} will now attempt to be committed")
        decompressBytes(compressedValue) match {
          case Success(value) => persistenceActor ! WriteTask(commitPromise, byteStringToString(key).sha256, value)
          case Failure(e) => context.log.error(s"Decompression error for key $key for reason: ${e.getLocalizedMessage}")
        }

      case OperationType.Delete(DeleteOp(key, uuid)) =>
        context.log.info(s"Delete entry with key $key and UUID ${uuid: String} will now attempt to be committed")
        persistenceActor ! DeleteTask(commitPromise, byteStringToString(key).sha256)

      case OperationType.Empty =>
        context.log.error("Received empty replicated operation type!")
    }
    commitPromise.future.map(_ => ())

  }) with ProtobufSerializer[ReplicatedOp] {
    override val messageCompanion: GeneratedMessageCompanion[ReplicatedOp] = ReplicatedOp
  }

  membershipActor ! DeclareReadiness
  context.log.info("Replication component initialized")


  /**
   * Receives messages upstream client CRUD requests
   *
   * @param clientReq the client operation information
   */
  override def onMessage(clientReq: ClientOperation): Behavior[ClientOperation] = clientReq match {

    case ReadOperation(readPromise: Promise[PersistenceData], key, uuid) =>

      // Get requests do not need to go through raft, so it directly goes to the persistence layer
      context.log.debug(s"Get request received with UUID ${uuid.string}")
      persistenceActor ! GetTask(readPromise, key.sha256)
      this

    case WriteOperation(writePromise: Promise[ReplicatedConfirmation], key, value, uuid) =>

      // Post requests must be committed by the raft group before it can be written to disk
      context.log.debug(s"Post request received with UUID ${uuid.string}")
      compressBytes(value) match {
        case Failure(e) => context.log.error(s"Compression error for key $key: ${e.getLocalizedMessage}")
        case Success(gzip) =>
          writePromise.completeWith(raft.submit(AppendEntryEvent(LogEntry(key.sha256.bytes, gzip), Some(uuid))).map(_ => ()))
      }
      this

    case DeleteOperation(deletePromise: Promise[ReplicatedConfirmation], key, uuid) =>

      // Delete requests also have to go through raft
      context.log.debug(s"Delete request received with UUID ${uuid.string}")
      deletePromise.completeWith(raft.submit(AppendEntryEvent(LogEntry(key, Array[Byte]()), Some(uuid))).map(_ => ()))
      this
  }

}

object ReplicationComponent {

  val REPLICATION_DIR: File = ServerConstants.BASE_DIRECTORY/"raft"

  val REPLICATED_LOG_INDEX: File = REPLICATION_DIR/"log.index"
  val REPLICATED_LOG_DATA: File  = REPLICATION_DIR/"log.data"

  def apply(
    membershipComponent: ActorRef[AdministrationMessage],
    persistenceComponent: ActorRef[PersistenceTask],
    addressRetriever: AddressRetriever
  ): Behavior[ClientOperation] =
    Behaviors.setup(new ReplicationComponent(_, membershipComponent, persistenceComponent, addressRetriever))


  /** Actor protocol */
  sealed trait ClientOperation {
    val uuid: UUID
  }

  type ReplicatedConfirmation = Unit

  case class ReadOperation(
    readPromise: Promise[PersistenceData],
    key: String,
    uuid: UUID,
  ) extends ClientOperation

  case class WriteOperation(
    writePromise: Promise[ReplicatedConfirmation],
    key: String,
    value: Array[Byte],
    uuid: UUID,
  ) extends ClientOperation

  case class DeleteOperation(
    deletePromise: Promise[ReplicatedConfirmation],
    key: String,
    uuid: UUID,
  ) extends ClientOperation
}