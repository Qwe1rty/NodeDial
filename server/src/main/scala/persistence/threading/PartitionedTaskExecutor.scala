package persistence.threading

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import persistence.io.IOTask
import persistence.threading.PartitionedTaskExecutor.PartitionedTask


object PartitionedTaskExecutor {

  private val PARTITION_SEED: Char = 0xAA // Hex representation of binary 10101010
  private val PARTITION_FUNCTION: String => Int = _.foldLeft(PARTITION_SEED)(_.^(_).toChar).toInt

  def apply(): Behavior[PartitionedTask] = Behaviors.setup(new PartitionedTaskExecutor(_))


  /** Actor protocol */
  private[persistence] case class PartitionedTask(hash: String, ioTask: IOTask)
}

class PartitionedTaskExecutor private(context: ActorContext[PartitionedTask]) extends AbstractBehavior[PartitionedTask](context) {

  import PartitionedTaskExecutor.PartitionedTask

  final private val threadCount: Int = Runtime.getRuntime.availableProcessors * 4
  final private val threads: Vector[ActorRef[IOTask]] = Vector.tabulate(threadCount) { id =>
    context.spawn(SingleThreadExecutor(id), f"singleThreadActor-${id}%03d")
  }

  context.log.info(s"$threadCount threads initialized for thread partitioner")


  override def onMessage(partitionedTask: PartitionedTask): Behavior[PartitionedTask] = {
    threads(PartitionedTaskExecutor.PARTITION_FUNCTION(partitionedTask.hash) % threadCount) ! partitionedTask.ioTask
    this
  }

}
