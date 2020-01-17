package partitioning

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}


object PartitionActor {

  def apply(persistenceActor: ActorRef)(implicit actorContext: ActorContext): ActorRef =
    actorContext.actorOf(
      Props(new PartitionActor(persistenceActor)),
      "partitionActor"
    )
}


class PartitionActor private(persistenceActor: ActorRef) extends Actor
                                                         with ActorLogging {
  private val partitionRing = ShardRing()

  // TODO subscribe to membership service

  log.info("Partition ring initialized")


  override def receive: Receive = ???
}
