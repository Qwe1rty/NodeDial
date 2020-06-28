package replication

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.pattern.ask
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import common.ServerDefaults.ACTOR_REQUEST_TIMEOUT
import org.slf4j.LoggerFactory
import schema.PortConfiguration.REPLICATION_PORT

import scala.concurrent.{ExecutionContext, Future}


object RaftServiceImpl {

  def apply(raftActor: ActorRef)(implicit actorSystem: ActorSystem): RaftService = {
    new RaftServiceImpl(raftActor)
  }
}


class RaftServiceImpl(raftActor: ActorRef)(implicit actorSystem: ActorSystem) extends RaftService {

  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  final private val log = LoggerFactory.getLogger(RaftServiceImpl.getClass)
  final private val service: HttpRequest => Future[HttpResponse] = RaftServiceHandler(this)

  Http()
    .bindAndHandleAsync(
      service,
      interface = "0.0.0.0",
      port = REPLICATION_PORT,
      connectionContext = HttpConnectionContext())
    .foreach(
      binding => log.info(s"Raft service bound to ${binding.localAddress}")
    )


  /**
   * AppendEntries is called by the leader to replicate log entries,
   * and as a heartbeat to prevent elections from happening
   */
  override def appendEntries(in: Source[AppendEntriesRequest, NotUsed]): Future[AppendEntriesResult] = {
    ???
  }

  /**
   * RequestVote is called by candidates to try and get a majority vote,
   * to become leader
   */
  override def requestVote(in: RequestVoteRequest): Future[RequestVoteResult] = {
    log.debug(s"Vote requested from candidate ${in.candidateId} with term ${in.candidateTerm}")

    // TODO check first if it matches basic recency requirements before sending request to actor

    (raftActor ? in)
      .mapTo[RequestVoteResult]
  }
}
