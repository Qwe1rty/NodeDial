package replication

import java.util.concurrent.TimeUnit

import administration.addresser.AddressRetriever
import administration.{Administration, Membership}
import akka.actor.Props
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.util.Timeout
import akka.{actor, util}
import better.files.File
import common.ServerConstants
import common.persistence.{ProtobufSerializer, Serializer}
import common.time.TimeRange
import io.jvm.uuid._
import org.slf4j.LoggerFactory
import replication.ConfigEntry.RaftNode
import replication.LogEntry.EntryType.Data
import replication.Raft.CommitFunction
import replication.eventlog.SimpleReplicatedLog
import replication.state.RaftState
import scalapb.GeneratedMessageCompanion
import schema.ImplicitGrpcConversions._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}


/**
 * The external API for Raft.
 *
 * @param addresser      the address retriever so Raft can check its own IP
 * @param commitCallback the function that the Raft process calls after it has determined that a
 *                       majority of the cluster has agreed to append the log entry, and now needs to be
 *                       applied to the state machine as dictated by user code
 * @param context        the parent actor context
 * @tparam Command the command type to be applied to the state machine
 */
class Raft[Command <: Serializable](addresser: AddressRetriever, commitCallback: CommitFunction[Command])(implicit context: ActorContext[_]) {

  /**
   * The serializer is used to convert the log entry bytes to the command object, for when
   * Raft determines an entry needs to be committed
   */
  this: Serializer[Command] =>

  implicit private val classicSystem: actor.ActorSystem = context.system.classicSystem

  import Raft._

  private val raft = context.actorOf(
    Props(new RaftFSM[Command](
      RaftState(
        Membership(Administration.nodeID, addresser.selfIP),
        new SimpleReplicatedLog(RAFT_LOG_INDEX, RAFT_LOG_DATA)
      ),
      commitCallback,
      addresser,
      this,
    )),
    s"raftActor-${UUID.random}"
  )

  RaftGRPCService(raft)
  LoggerFactory.getLogger(Raft.getClass).info("Raft API service has been initialized")


  def submit(key: String, command: Command, uuid: Option[UUID] = None): Future[AppendEntryAck] = {
    implicit def timeout: util.Timeout = Timeout(Raft.NEW_LOG_ENTRY_TIMEOUT)

    serialize(command) match {
      case Failure(exception) => Future.failed(exception)
      case Success(value) =>
        val appendEntryData = Data(DataEntry(key, value))
        val appendEntryEvent = AppendEntryEvent(LogEntry(appendEntryData), uuid.map(UUIDToByteString))
        (raft ? appendEntryEvent).mapTo[AppendEntryAck]
    }
  }

  def join(joinInfo: RaftNode): Unit =
    raft ! AddNodeEvent(joinInfo)
}

object Raft {

  /**
   * The commit function is called after the Raft process has determined a majority of the
   * servers have agreed to append the log entry, and now needs to be applied to the state
   * machine as dictated by user code
   */
  type CommitConfirmation = Unit
  type CommitFunction[Command] = Function[(Command, LoggingAdapter), Future[CommitConfirmation]]

  val RAFT_DIR: File = ServerConstants.BASE_DIRECTORY/"raft"
  val RAFT_LOG_INDEX: File = RAFT_DIR/"raft.log.index"
  val RAFT_LOG_DATA: File  = RAFT_DIR/"raft.log.data"

  val ELECTION_TIMEOUT_LOWER_BOUND: FiniteDuration = FiniteDuration(150, TimeUnit.MILLISECONDS)
  val ELECTION_TIMEOUT_UPPER_BOUND: FiniteDuration = FiniteDuration(325, TimeUnit.MILLISECONDS)

  val ELECTION_TIMEOUT_RANGE: TimeRange = TimeRange(ELECTION_TIMEOUT_LOWER_BOUND, ELECTION_TIMEOUT_UPPER_BOUND)
  val INDIVIDUAL_NODE_TIMEOUT: FiniteDuration = FiniteDuration(50, TimeUnit.MILLISECONDS)
  val NEW_LOG_ENTRY_TIMEOUT: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)

  private[replication] object LogEntrySerializer extends ProtobufSerializer[LogEntry] {
    override def messageCompanion: GeneratedMessageCompanion[LogEntry] = LogEntry
  }
}
