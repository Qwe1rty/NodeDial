//package partitioning
//
//import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
//import common.DefaultActor
//
//
//object ShardingComponent {
//
//  def apply(persistenceActor: ActorRef)(implicit actorContext: ActorContext): ActorRef =
//    actorContext.actorOf(
//      Props(new ShardingComponent(persistenceActor)),
//      "partitionActor"
//    )
//}
//
//
//class ShardingComponent private(
//    persistenceActor: ActorRef
//  )
//  extends DefaultActor
//  with ActorLogging {
//
//  private val shardRing = ShardRing()
//
//  // TODO subscribe to administration service
//
//  log.info("Partition ring initialized")
//
//
//  override def receive: Receive = ???
//}
