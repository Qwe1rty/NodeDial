package replication

import akka.actor.ActorSystem
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import better.files.File
import com.roundeights.hasher.Implicits._
import common.ServerConstants
import common.persistence.{Compression, ProtobufSerializer}
import io.jvm.uuid
import membership.MembershipActor
import membership.addresser.AddressRetriever
import membership.api.{Membership, MembershipAPI}
import persistence.PersistenceComponent.{DeleteTask, GetTask, PersistenceData, PersistenceFuture, PersistenceTask, PostTask}
import persistence.PersistenceComponent
import replication.Raft.CommitConfirmation
import replication.ReplicatedOp.OperationType
import scalapb.GeneratedMessageCompanion
import schema.ImplicitGrpcConversions._
import schema.service.Request
import service.OperationPackage

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success}


object ReplicationActor {

  val REPLICATION_DIR: File = ServerConstants.BASE_DIRECTORY/"raft"

  val REPLICATED_LOG_INDEX: File = REPLICATION_DIR/"context.log.index"
  val REPLICATED_LOG_DATA: File  = REPLICATION_DIR/"context.log.data"


  def apply(
      membershipActor: ActorRef[MembershipAPI],
      persistenceActor: ActorRef[PersistenceTask],
      addressRetriever: AddressRetriever
    ): Behavior[OperationPackage] =
    Behaviors.setup(new ReplicationActor(_, membershipActor, persistenceActor, addressRetriever))
}


class ReplicationActor(
    context: ActorContext[OperationPackage],
    membershipActor: ActorRef[MembershipAPI],
    persistenceActor: ActorRef[PersistenceTask],
    addressRetriever: AddressRetriever,
  )
  extends AbstractBehavior[OperationPackage](context)
  with Compression {

  implicit private val classicSystem: ActorSystem = context.system.classicSystem
  implicit private val executionContext: ExecutionContext = context.system.executionContext

  private val raft = new Raft[ReplicatedOp](addressRetriever, { commit =>
    val commitPromise = Promise[PersistenceData]()

    commit.operationType match {
      case OperationType.Read(ReadOp(key, uuid, _)) =>
        context.log.info(s"Get entry with key $key and UUID ${uuid: String} has been received as Raft commit")
        persistenceActor ! GetTask(commitPromise, byteStringToString(key).sha256)

      case OperationType.Write(WriteOp(key, compressedValue, uuid, _)) =>
        context.log.info(s"Write entry with key $key and UUID ${uuid: String} will now attempt to be committed")
        decompressBytes(compressedValue) match {
          case Success(value) => persistenceActor ! PostTask(commitPromise, byteStringToString(key).sha256, value)
          case Failure(e) => context.log.error(s"Decompression error for key $key for reason: ${e.getLocalizedMessage}")
        }

      case OperationType.Delete(DeleteOp(key, uuid, _)) =>
        context.log.info(s"Delete entry with key $key and UUID ${uuid: String} will now attempt to be committed")
        persistenceActor ! DeleteTask(commitPromise, byteStringToString(key).sha256)

      case OperationType.Empty =>
        context.log.error("Received empty replicated operation type!")
    }
    commitPromise.future.map(_ => ())

  }) with ProtobufSerializer[ReplicatedOp] {
    override val messageCompanion: GeneratedMessageCompanion[ReplicatedOp] = ReplicatedOp
  }


  /**
   * Receives messages upstream client CRUD requests
   *
   * @param operationPackage the client operation information
   */
  override def onMessage(operationPackage: OperationPackage): Behavior[OperationPackage] = operationPackage.operation match {

    case Request.GetRequest(key) =>

      // Get requests do not need to go through raft, so it directly goes to the persistence layer
      context.log.debug(s"Get request received with UUID ${uuid.string}")

//      context.ask(persistenceActor, (ref: ActorRef[PersistenceFuture]) => GetTask(ref, key.sha256)) {
//
//      }


      persistenceActor ! GetTask(context, key.sha256)

    case Request.PostRequest(key, value) =>

      // Post requests must be committed by the raft group before it can be written to disk
      context.log.debug(s"Post request received with UUID ${uuid.string}")
      pendingRequestActors += uuid -> requestActor

      compressBytes(value) match {
        case Success(gzip) => super.submit(AppendEntryEvent(context.logEntry(key, gzip), Some(uuid))).onComplete {

        }
        case Failure(e) => context.log.error(s"Compression error for key $key: ${e.getLocalizedMessage}")
      }

    case Request.DeleteRequest(key) =>

      // Delete requests also have to go through raft
      context.log.debug(s"Delete request received with UUID ${uuid.string}")
      pendingRequestActors += uuid -> requestActor

      super.receive(AppendEntryEvent(context.logEntry(key, Array[Byte]()), Some(uuid)))
  }

}