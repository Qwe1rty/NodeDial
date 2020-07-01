package persistence.threading

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import common.utils.DefaultActor
import persistence.io.IOTask


object ThreadPartitionActor {

  case class PartitionedTask(hash: String, ioTask: IOTask)

  private val PARTITION_SEED: Char = 0xAA // Hex representation of binary 10101010
  private val PARTITION_FUNCTION: String => Int = _.foldLeft(PARTITION_SEED)(_.^(_).toChar).toInt


  def apply()(implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(
      Props(new ThreadPartitionActor),
      "threadPartitionActor"
    )
}


class ThreadPartitionActor private()
  extends DefaultActor
  with ActorLogging {

  import ThreadPartitionActor.PartitionedTask

  final private val threadCount: Int = Runtime.getRuntime.availableProcessors * 4
  final private val threads: Vector[ActorRef] = Vector.tabulate(threadCount)(SingleThreadActor(_))

  log.info(s"${threadCount} threads initialized for thread partitioner")


  override def receive: Receive = {

    case PartitionedTask(hash: String, ioTask: IOTask) =>
      threads(ThreadPartitionActor.PARTITION_FUNCTION(hash) % threadCount) ! ioTask

    case x => log.error(receivedUnknown(x))
  }
}
