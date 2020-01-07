package common.modules.membership

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.{ActorMaterializer, Materializer}
import common.ChordialDefaults
import org.slf4j.LoggerFactory
import service.RequestServiceImpl

import scala.concurrent.{ExecutionContext, Future}


object MembershipServiceImpl {

  def apply(membershipActor: ActorRef)(implicit actorSystem: ActorSystem): MembershipService =
    new MembershipServiceImpl(membershipActor)
}


class MembershipServiceImpl(membershipActor: ActorRef)(implicit actorSystem: ActorSystem) extends MembershipService {

  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  final private val log = LoggerFactory.getLogger(RequestServiceImpl.getClass)
  final private val service: HttpRequest => Future[HttpResponse] = MembershipServiceHandler(this)

  Http()
    .bindAndHandleAsync(
      service,
      interface = "127.0.0.1",
      port = ChordialDefaults.MEMBERSHIP_PORT,
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
  override def fullSync(in: FullSyncRequest): Future[SyncResponse] = ???

  /**
   * Pull-based synchronization RPC, for passive updates
   */
  override def updateSync(in: UpdateRequest): Future[SyncResponse] = ???
}
