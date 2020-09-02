package administration

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import com.risksense.ipaddr.IpAddress
import common.ServerDefaults.ACTOR_REQUEST_TIMEOUT
import common.administration._
import Administration.{AdministrationMessage, GetClusterInfo}
import akka.stream.Materializer
import org.slf4j.LoggerFactory
import schema.PortConfiguration.MEMBERSHIP_PORT

import scala.concurrent.{ExecutionContext, Future}


class AdministrationGRPCService(administration: ActorRef[AdministrationMessage])(implicit actorSystem: ActorSystem[_]) extends AdministrationService {

  implicit val executionContext: ExecutionContext = actorSystem.executionContext

  final private val log = LoggerFactory.getLogger(AdministrationGRPCService.getClass)
  final private val service: HttpRequest => Future[HttpResponse] = AdministrationServiceHandler(this)(
    Materializer.matFromSystem(actorSystem),
    actorSystem.classicSystem
  )

  Http()(actorSystem.classicSystem)
    .bindAndHandleAsync(service, interface = "0.0.0.0", port = MEMBERSHIP_PORT, HttpConnectionContext())
    .foreach(binding => log.info(s"Membership service bound to ${binding.localAddress}"))


  /**
   * Push-based synchronization RPC
   */
  override def publish(event: Event): Future[EventReply] = {
    log.debug(s"Event received from ${event.nodeId}, forwarding to membership actor")

    administration ! event
    Future.successful(EventReply())
  }

  /**
   * Pull-based synchronization RPC, for full recovery situations
   */
  override def fullSync(in: FullSyncRequest): Future[SyncResponse] = {
    log.info(s"Full sync requested from node ${in.nodeId} with IP ${IpAddress(in.ipAddress).toString}")

    administration
      .ask(GetClusterInfo(_: ActorRef[Seq[SyncInfo]]))
      .map(SyncResponse(_))
  }

  /**
   * Pull-based synchronization RPC, for passive updates
   */
  override def updateSync(in: UpdateRequest): Future[SyncResponse] = ???
}

object AdministrationGRPCService {

  def apply(administration: ActorRef[AdministrationMessage])(implicit actorSystem: ActorSystem[_]): AdministrationService =
    new AdministrationGRPCService(administration)
}