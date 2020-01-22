package membership

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.pattern.ask
import akka.stream.{ActorMaterializer, Materializer}
import com.risksense.ipaddr.IpAddress
import common.ServerDefaults.ACTOR_REQUEST_TIMEOUT
import common.membership._
import org.slf4j.LoggerFactory
import schema.PortConfiguration.MEMBERSHIP_PORT

import scala.concurrent.{ExecutionContext, Future}


object MembershipServiceImpl {

  def apply(membershipActor: ActorRef)(implicit actorSystem: ActorSystem): MembershipService =
    new MembershipServiceImpl(membershipActor)
}


class MembershipServiceImpl(membershipActor: ActorRef)(implicit actorSystem: ActorSystem) extends MembershipService {

  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  final private val log = LoggerFactory.getLogger(MembershipServiceImpl.getClass)
  final private val service: HttpRequest => Future[HttpResponse] = MembershipServiceHandler(this)

  Http()
    .bindAndHandleAsync(
      service,
      interface = "0.0.0.0",
      port = MEMBERSHIP_PORT,
      connectionContext = HttpConnectionContext())
    .foreach(
      binding => log.info(s"Membership service bound to ${binding.localAddress}")
    )


  /**
   * Push-based synchronization RPC
   */
  override def publish(event: Event): Future[EventReply] = {

    membershipActor ! event
    Future.successful(EventReply())
  }

  /**
   * Pull-based synchronization RPC, for full recovery situations
   */
  override def fullSync(in: FullSyncRequest): Future[SyncResponse] = {
    log.info(s"Full sync requested from node ${in.nodeId} with IP ${IpAddress(in.ipAddress).toString}")

    (membershipActor ? MembershipAPI.GetClusterInfo)
      .mapTo[Seq[SyncInfo]]
      .map(SyncResponse(_))
  }

  /**
   * Pull-based synchronization RPC, for passive updates
   */
  override def updateSync(in: UpdateRequest): Future[SyncResponse] = ???
}
