package persistence.threading

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext


object SingleThreadExecutor {

  def apply(): SingleThreadExecutor = new SingleThreadExecutor
}


class SingleThreadExecutor extends ExecutionContext {

  private val threadExecutor = Executors.newFixedThreadPool(1)


  override def execute(runnable: Runnable): Unit =
    threadExecutor.submit(runnable)

  override def reportFailure(cause: Throwable): Unit = ???
    // TODO log error

  def shutdown(): Unit = threadExecutor.shutdown()
}
