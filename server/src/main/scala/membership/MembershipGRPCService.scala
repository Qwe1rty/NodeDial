package membership

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import com.risksense.ipaddr.IpAddress
import common.ServerDefaults.ACTOR_REQUEST_TIMEOUT
import common.membership._
import membership.Administration.{AdministrationMessage, GetClusterInfo}
import org.slf4j.LoggerFactory
import schema.PortConfiguration.MEMBERSHIP_PORT

import scala.concurrent.{ExecutionContext, Future}


class MembershipGRPCService(administration: ActorRef[AdministrationMessage])(implicit actorSystem: ActorSystem[_]) extends MembershipService {

  implicit val executionContext: ExecutionContext = actorSystem.executionContext

  final private val log = LoggerFactory.getLogger(MembershipGRPCService.getClass)
  final private val service: HttpRequest => Future[HttpResponse] = MembershipServiceHandler(this)

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
      .ask((ref: ActorRef[Seq[SyncInfo]]) => GetClusterInfo(ref))
      .map(SyncResponse(_))
  }

  /**
   * Pull-based synchronization RPC, for passive updates
   */
  override def updateSync(in: UpdateRequest): Future[SyncResponse] = ???
}

object MembershipGRPCService {

  def apply(administration: ActorRef[AdministrationMessage])(implicit actorSystem: ActorSystem[_]): MembershipService =
    new MembershipGRPCService(administration)
}