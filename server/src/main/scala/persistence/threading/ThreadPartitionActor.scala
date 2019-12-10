package persistence.threading

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import common.utils.ActorDefaults
import persistence.io.IOTask


object ThreadPartitionActor {

  private val PARTITION_SEED: Char = 0xAA // Hex representation of binary 10101010
  private val PARTITION_FUNCTION: String => Int = _.foldLeft(PARTITION_SEED)(_.^(_).toChar).toInt


  private def props: Props = Props(new ThreadPartitionActor)

  def apply()(implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(props, "threadPartitionActor")
}


class ThreadPartitionActor extends Actor with ActorLogging with ActorDefaults {

  final private val threadCount: Int = Runtime.getRuntime.availableProcessors * 4
  final private val threads: Vector[ActorRef] = Vector.tabulate(threadCount)(SingleThreadActor(_))

  log.info(s"${threadCount} threads initialized for thread partitioner")


  override def receive: Receive = {

    case (hash: String, ioTask: IOTask) =>
      threads(ThreadPartitionActor.PARTITION_FUNCTION(hash) % threadCount) ! ioTask

    case x => log.error(receivedUnknown(x))
  }
}
