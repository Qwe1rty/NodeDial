package persistence.io

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import better.files.File
import persistence.PersistenceComponent.{DeleteTask, GetTask, PersistenceData, PersistenceTask, WriteTask}
import persistence._
import persistence.io.KeyStateManager.KeyStateAction
import persistence.execution.PartitionedTaskExecutor.PartitionedTask

import scala.collection.mutable
import scala.concurrent.Promise
import scala.language.implicitConversions
import scala.util.Try


class KeyStateManager private(
    context: ActorContext[KeyStateAction],
    executor: ActorRef[PartitionedTask],
    hash: String
  )
  extends AbstractBehavior[KeyStateAction](context) {

  import KeyStateManager._

  final private val tag = s"$hash ->" // TODO patternize this

  private var exclusiveLocked = false // TODO make this a 2PL
  private var pendingRequest: Option[Promise[PersistenceData]] = None
  private val requestQueue = mutable.Queue[PersistenceTask]()


  implicit private def fileOf(extension: String): File =
    PersistenceComponent.PERSISTENCE_DIRECTORY/(hash + extension)

  /**
   * Schedules a task to run in the provided execution context
   *
   * @param task task to schedule
   */
  private def schedule(task: IOTask): Unit = {
    executor ! PartitionedTask(hash, task)
    context.log.debug(s"$tag Submitting task to task scheduler")
  }

  /**
   * Suspends the execution by unlocking the actor
   */
  private def suspend(): Unit = {
    exclusiveLocked = false
    pendingRequest = None
    context.log.debug(s"$tag Suspending actor")
  }

  /**
   * Processes the next task to run, without checking if one exists
   */
  private def process(): Unit = {
    val nextTask = requestQueue.dequeue()

    exclusiveLocked = true
    pendingRequest = Some(nextTask.requestPromise)

    schedule(nextTask match {

      case _: GetTask =>
        context.log.info(s"$tag Signalling read task")
        ReadIOTask(VALUE_EXTENSION)(context.self)

      case post: WriteTask =>
        context.log.info(s"$tag Signalling write task with hex value: ${post.value.map("%02X" format _).mkString}")
        WriteIOTask(VALUE_EXTENSION, post.value)(context.self)

      case _: DeleteTask =>
        context.log.info(s"$tag Signalling delete task")
        DeleteIOTask(VALUE_EXTENSION)(context.self)
    })
  }

  /**
   * Executes the next task, if there is one
   */
  private def poll(): Unit = {
    context.log.debug(s"$tag Polling next operation")
    if (requestQueue.isEmpty) suspend() else process()
  }

  /**
   * Completes the pending request by sending the result back
   *
   * @param result the request result
   */
  private def complete(result: Try[PersistenceData]): Unit =
    pendingRequest.get.complete(result)


  override def onMessage(action: KeyStateAction): Behavior[KeyStateAction] = action match {
    case Left(persistenceTask) =>
      context.log.info(s"$tag Persistence task received")
      requestQueue.enqueue(persistenceTask)
      if (!exclusiveLocked) process()
      this

    case Right(signal) => signal match {
      case ReadCompleteSignal(result) =>
        context.log.debug(s"$tag Read complete signal received")
        complete(result)
        poll()

      case WriteCompleteSignal(result) =>
        context.log.debug(s"$tag Write complete signal received")
        complete(result.map(_ => None))
        poll()

      case DeleteCompleteSignal(result) =>
        context.log.debug(s"$tag Delete complete signal received")
        complete(result.map(_ => None))
        poll()
      }
      this
  }

}

object KeyStateManager {

  private val VALUE_EXTENSION = ".val"

  def apply(taskExecutor: ActorRef[PartitionedTask], hash: String): Behavior[KeyStateAction] =
    Behaviors.setup(new KeyStateManager(_, taskExecutor, hash))


  /** Actor protocol */
  type KeyStateAction = Either[PersistenceTask, IOSignal]
  private[persistence] sealed trait IOSignal

  /**
   * Signal to the key state manager that the disk read job has completed, along with the result
   *
   * @param result the result of the disk read
   */
  private[io] final case class ReadCompleteSignal(
    result: Try[Option[Array[Byte]]]
  ) extends IOSignal

  /**
   * Signal to the key state manager that the disk write job has completed
   *
   * @param result the result of the disk write
   */
  private[io] final case class WriteCompleteSignal(
    result: Try[Unit]
  ) extends IOSignal

  /**
   * Signal to the key state manager that the disk delete job has completed
   *
   * @param result the result of the disk write
   */
  private[io] final case class DeleteCompleteSignal(
    result: Try[Unit]
  ) extends IOSignal
}