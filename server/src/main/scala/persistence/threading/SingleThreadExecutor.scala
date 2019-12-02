package persistence.threading

import java.util.concurrent.Executors

import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext


object SingleThreadExecutor {

  def apply(id: Int): SingleThreadExecutor = new SingleThreadExecutor(id)
}


class SingleThreadExecutor(id: Int) extends ExecutionContext {

  final private val tag = s"${id} -> " // TODO patternize this

  private val log = LoggerFactory.getLogger(SingleThreadExecutor.getClass)
  private val threadExecutor = Executors.newFixedThreadPool(1)

  log.info(s"Thread executor wrapper created with ID: ${id}")


  override def execute(runnable: Runnable): Unit = {
    threadExecutor.submit(runnable)
    log.debug(tag + "Task submitted")
  }

  override def reportFailure(cause: Throwable): Unit =
    log.error(tag + s"Exception occurred in executor: ${cause.getMessage}")

  def shutdown(): Unit = threadExecutor.shutdown()
}
