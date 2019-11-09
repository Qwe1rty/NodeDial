package persistence.threading

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.ActorMaterializer
import persistence.io.IOTask


object ThreadPartitionActor {

  final private val PARTITION_SEED: Char = 0xAA // Hex representation of binary 10101010
  final private val PARTITION_FUNCTION: String => Int = _.foldLeft(PARTITION_SEED)(_ ^ _ toChar) toInt


  def props: Props = Props(new ThreadPartitionActor)
}


class ThreadPartitionActor extends Actor with ActorLogging {

  implicit final private val materializer: ActorMaterializer = ActorMaterializer()

  private val coreCount: Int = Runtime.getRuntime.availableProcessors
  private val threads: Vector[ActorRef] = Vector.fill(coreCount)(context.actorOf(SingleThreadActor.props))


  override def receive: Receive = {

    case (hash: String, ioTask: IOTask) => {
      threads(ThreadPartitionActor.PARTITION_FUNCTION(hash) % coreCount) ! ioTask
    }

    case _ => ??? // TODO log error
  }
}
