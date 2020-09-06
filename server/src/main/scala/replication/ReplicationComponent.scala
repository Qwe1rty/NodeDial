package replication

import administration.Administration.{AdministrationMessage, DeclareReadiness}
import administration.addresser.AddressRetriever
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.roundeights.hasher.Implicits._
import common.persistence.{Compression, ProtobufSerializer}
import io.jvm.uuid._
import persistence.PersistenceComponent._
import replication.ReplicatedOp.OperationType
import replication.ReplicatedOp.OperationType.{Delete, Write}
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
  with ProtobufSerializer[ReplicatedOp]
  with Compression { self =>

  import ReplicationComponent._

  implicit private val classicSystem: ActorSystem = context.system.classicSystem
  implicit private val executionContext: ExecutionContext = context.system.executionContext

  override val messageCompanion: GeneratedMessageCompanion[ReplicatedOp] = ReplicatedOp

  // TODO use a Raft-provided logger when in Raft context
  private val raft: Raft[ReplicatedOp] = new Raft[ReplicatedOp](addressRetriever, { case (commit, log)  =>

    val commitPromise = Promise[PersistenceData]()
    commit.operationType match {

      case OperationType.Read(ReadOp(key, uuid)) =>
        val keyString = byteStringToString(key)
        val uuidString = byteStringToUUID(uuid)

        log.info(s"Get entry with key $keyString and UUID $uuidString has been received as Raft commit")
        persistenceActor ! GetTask(commitPromise, keyString.sha256)

      case OperationType.Write(WriteOp(key, compressedValue, uuid)) =>
        val keyString = byteStringToString(key)
        val uuidString = byteStringToUUID(uuid)

        log.info(s"Write entry with key $keyString and UUID $uuidString will now attempt to be committed")
        decompressBytes(compressedValue) match {
          case Success(value) => persistenceActor ! WriteTask(commitPromise, keyString.sha256, value)
          case Failure(e) => log.error(s"Decompression error for key $key for reason: ${e.getLocalizedMessage}")
        }

      case OperationType.Delete(DeleteOp(key, uuid)) =>
        val keyString = byteStringToString(key)
        val uuidString = byteStringToUUID(uuid)

        log.info(s"Delete entry with key $keyString and UUID $uuidString will now attempt to be committed")
        persistenceActor ! DeleteTask(commitPromise, keyString.sha256)

      case OperationType.Empty =>
        log.error("Received empty replicated operation type!")
    }
    commitPromise.future.map { _ => () }

  })(context) with ProtobufSerializer[ReplicatedOp] {
    override val messageCompanion: GeneratedMessageCompanion[ReplicatedOp] = self.messageCompanion
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
          val futureConfirmation = raft.submit(key, ReplicatedOp(Write(WriteOp(key, gzip, uuid))), Some(uuid.toString))
          writePromise.completeWith(futureConfirmation.map { _ => () })
      }
      this

    case DeleteOperation(deletePromise: Promise[ReplicatedConfirmation], key, uuid) =>

      // Delete requests also have to go through raft
      context.log.debug(s"Delete request received with UUID ${uuid.string}")
      val futureConfirmation = raft.submit(key, ReplicatedOp(Delete(DeleteOp(key, uuid))), Some(uuid.toString))
      deletePromise.completeWith(futureConfirmation.map { _ => () })
      this
  }

}

object ReplicationComponent {

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