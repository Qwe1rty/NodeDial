package persistence.threading

import java.util.concurrent.Executors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import persistence.io.IOTask

import scala.concurrent.ExecutionContext


object SingleThreadExecutor {

  def apply(id: Int): Behavior[IOTask] = Behaviors.setup(new SingleThreadExecutor(_, id))
}

class SingleThreadExecutor(context: ActorContext[IOTask], id: Int) extends AbstractBehavior[IOTask](context) {

  final private val tag: String = s"Thread ID ${id} -> "
  final private val ec: ExecutionContext = new ExecutionContext {

    private val threadExecutor = Executors.newFixedThreadPool(1)
    context.log.info(s"Thread executor wrapper created with ID: ${id}")

    override def execute(runnable: Runnable): Unit = {
      threadExecutor.submit(runnable)
      context.log.debug(tag + "Task submitted")
    }

    override def reportFailure(cause: Throwable): Unit =
      context.log.error(tag + s"Exception occurred in executor: ${cause.getMessage}")

    def shutdown(): Unit = threadExecutor.shutdown()
  }

  context.log.info(s"Thread actor created with ID: ${id}")


  override def onMessage(ioTask: IOTask): Behavior[IOTask] = {
    context.log.debug(tag + s"IO task received by thread actor")
    ioTask.execute()(ec)
    this
  }
}
