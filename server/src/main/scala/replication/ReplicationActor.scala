package replication

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import better.files.File
import com.roundeights.hasher.Implicits._
import common.ServerConstants
import common.persistence.{Compression, ProtobufSerializer}
import io.jvm.uuid
import io.jvm.uuid._
import membership.MembershipActor
import membership.addresser.AddressRetriever
import membership.api.{Membership, MembershipAPI}
import persistence.PersistenceComponent.{GetTask, PersistenceTask}
import persistence.{DeleteTask, GetTask, PersistenceComponent, PostTask}
import replication.ReplicatedOp.OperationType
import replication.eventcontext.log.SimpleReplicatedcontext.log
import scalapb.GeneratedMessageCompanion
import schema.ImplicitGrpcConversions._
import schema.service.Request
import service.OperationPackage

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
  //  extends RaftActor[ReplicatedOp](
  //    Membership(MembershipActor.nodeID, addressRetriever.selfIP),
  //    new SimpleReplicatedcontext.log(ReplicationActor.REPLICATED_context.log_INDEX, ReplicationActor.REPLICATED_context.log_DATA)
  //  )
  with ProtobufSerializer[ReplicatedOp]
  with Compression {

//  val raft:


  override val messageCompanion: GeneratedMessageCompanion[ReplicatedOp] = ReplicatedOp

  /**
   * Receives messages upstream client CRUD requests
   *
   * @param operationPackage the client operation information
   */
  override def onMessage(operationPackage: OperationPackage): Behavior[OperationPackage] = operationPackage.operation match {

    case Request.GetRequest(key) =>

      // Get requests do not need to go through raft, so it directly goes to the persistence layer
      context.log.debug(s"Get request received with UUID ${uuid.string}")
      persistenceActor ! GetTask(Some(requestActor), key.sha256)

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

  /**
   * The commit function is called after the Raft process has determined a majority of the
   * servers have agreed to append the context.log entry, and now needs to be applied to the state
   * machine as dictated by user code
   */
  override def commit: Commit = { case ReplicatedOp(operation) => operation match {

    case OperationType.Read(ReadOp(key, uuid)) =>
      context.log.info(s"Get entry with key $key and UUID ${uuid: String} has been received as Raft commit")

      val requestActor = pendingRequestActors.get(uuid)
      persistenceActor ! GetTask(requestActor, byteStringToString(key).sha256)

    case OperationType.Write(WriteOp(key, compressedValue, uuid)) =>
      context.log.info(s"Write entry with key $key and UUID ${uuid: String} will now attempt to be committed")

      val requestActor = pendingRequestActors.get(uuid)
      decompressBytes(compressedValue) match {
        case Success(value) => persistenceActor ! PostTask(requestActor, byteStringToString(key).sha256, value)
        case Failure(e) => context.log.error(s"Decompression error for key $key for reason: ${e.getLocalizedMessage}")
      }

    case OperationType.Delete(DeleteOp(key, uuid)) =>
      context.log.info(s"Delete entry with key $key and UUID ${uuid: String} will now attempt to be committed")

    val requestActor = pendingRequestActors.get(uuid)
      persistenceActor ! DeleteTask(requestActor, byteStringToString(key).sha256)

    case OperationType.Empty =>
      context.log.error("Received empty replicated operation type!")
  }}

}