package common.modules.membership

import akka.actor.ActorRef

import scala.concurrent.Future


class MembershipServiceImpl(membershipActor: ActorRef) extends MembershipService {

  /**
   * Push-based synchronization RPC
   */
  override def publish(in: Event): Future[EventReply] = {

    ???
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
