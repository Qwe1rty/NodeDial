package replication

import akka.actor.{ActorRef, ActorSystem}
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.pattern.ask
import akka.stream.Materializer
import com.risksense.ipaddr.IpAddress
import common.ServerDefaults.ACTOR_REQUEST_TIMEOUT
import common.rpc.GRPCSettingsFactory
import org.slf4j.LoggerFactory
import replication.LogEntry.EntryType
import schema.ImplicitDataConversions._
import schema.PortConfiguration.REPLICATION_PORT

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}


object RaftGRPCService extends GRPCSettingsFactory {

  def apply(raftActor: ActorRef)(implicit actorSystem: ActorSystem): RaftService = {
    new RaftGRPCService(raftActor)
  }

  override def createGRPCSettings
    (ipAddress: IpAddress, timeout: FiniteDuration)(implicit actorSystem: ActorSystem): GrpcClientSettings = {
    GrpcClientSettings.connectToServiceAt(ipAddress, REPLICATION_PORT).withDeadline(timeout)
  }
}


class RaftGRPCService(raftActor: ActorRef)(implicit actorSystem: ActorSystem) extends RaftService {

  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  final private val log = LoggerFactory.getLogger(RaftGRPCService.getClass)
  final private val service: HttpRequest => Future[HttpResponse] = RaftServiceHandler(this)(
    Materializer.matFromSystem(actorSystem),
    actorSystem.classicSystem
  )

  Http()
    .bindAndHandleAsync(service, interface = "0.0.0.0", port = REPLICATION_PORT, HttpConnectionContext())
    .foreach(binding => log.info(s"Raft service bound to ${binding.localAddress}"))


  /**
   * Handles a new write request from client - starts an AppendEntries
   * broadcast if leader for replication, or redirects it to the leader (if
   * existing).
   */
  override def newLogWrite(in: AppendEntryEvent): Future[AppendEntryAck] = {
    in.logEntry.entryType match {
      case EntryType.Data(dataEntry) => log.debug(s"New log write event with key ${dataEntry.key}")
      case EntryType.Cluster(_)      => log.debug("New Raft cluster reconfiguration event received")
      case EntryType.Empty =>
        log.error("New log write event received an invalid message")
    }

    (raftActor ? in)
      .mapTo[AppendEntryAck]
  }

  /**
   * AddNode is called by the server admin (directly or indirectly)
   */
  override def addNode(in: AddNodeEvent): Future[AddNodeAck] = {
    log.debug(s"Add node request received for ${in.node.nodeId} with address ${IpAddress(in.node.ipAddress).toString}")

    (raftActor ? in)
      .mapTo[AddNodeAck]
  }


  /**
   * RequestVote is called by candidates to try and get a majority vote,
   * to become leader
   */
  override def requestVote(in: RequestVoteRequest): Future[RequestVoteResult] = {
    log.debug(s"Vote requested from candidate ${in.candidateId} with term ${in.candidateTerm}")

    (raftActor ? in)
      .mapTo[RequestVoteResult]
  }

  /**
   * AppendEntries is called by the leader to replicate log entries,
   * and as a heartbeat to prevent elections from happening
   */
  override def appendEntries(in: AppendEntriesRequest): Future[AppendEntriesResult] = {
    log.debug(
      s"Append entries request from leader ${in.leaderId} with latest log entry: " +
      s"(${in.prevLogTerm}, ${in.prevLogIndex})"
    )

    (raftActor ? in)
      .mapTo[AppendEntriesResult]
  }
}
