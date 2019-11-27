package persistence.threading

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import persistence.io.IOTask


object ThreadPartitionActor {

  private val PARTITION_SEED: Char = 0xAA // Hex representation of binary 10101010
  private val PARTITION_FUNCTION: String => Int = _.foldLeft(PARTITION_SEED)(_.^(_).toChar).toInt


  def apply()(implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(props, "threadPartitionActor")

  def props: Props = Props(new ThreadPartitionActor)
}


class ThreadPartitionActor extends Actor with ActorLogging {

  final private val coreCount: Int = Runtime.getRuntime.availableProcessors
  final private val threads: Vector[ActorRef] = Vector.fill(coreCount * 4)(SingleThreadActor())


  override def receive: Receive = {

    case (hash: String, ioTask: IOTask) =>
      threads(ThreadPartitionActor.PARTITION_FUNCTION(hash) % coreCount) ! ioTask

    case _ => ??? // TODO log error
  }
}
