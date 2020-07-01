package persistence.io

import akka.actor.{ActorContext, ActorLogging, ActorPath, ActorRef, Props}
import better.files.File
import common.utils.DefaultActor
import persistence.{PersistenceActor, _}
import persistence.threading.ThreadPartitionActor.PartitionedTask
import service.RequestActor.ResultType

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.Failure


object KeyStateActor {

  private val WRITE_AHEAD_EXTENSION = ".wal"
  private val VALUE_EXTENSION = ".val"


  def apply(executorActor: ActorRef, hash: String)(implicit actorContext: ActorContext): ActorRef =
    actorContext.actorOf(
      Props(new KeyStateActor(executorActor, hash)),
      s"keyStateActor-${hash}"
    )
}


class KeyStateActor private(
    executorActor: ActorRef,
    hash: String
  )
  (implicit actorContext: ActorContext)
  extends DefaultActor
  with ActorLogging {

  import KeyStateActor._

  final private val tag = s"${hash} -> " // TODO patternize this

  private val requestQueue = mutable.Queue[PersistenceTask]()
  private var exclusiveLocked = false // TODO make this a 2PL
  private var pendingRequest: Option[ActorPath] = None


  implicit private def fileOf(extension: String): File =
    PersistenceActor.PERSISTENCE_DIRECTORY/(hash + extension)

  /**
   * Schedules a task to run in the provided execution context
   *
   * @param task task to schedule
   */
  private def schedule(task: IOTask): Unit = {
    executorActor ! PartitionedTask(hash, task)
    log.debug(tag + s"Submitting task to task scheduler")
  }

  /**
   * Suspends the execution by unlocking the actor
   */
  private def suspend(): Unit = {
    exclusiveLocked = false
    pendingRequest = None
    log.debug(tag + "Suspending actor")
  }

  /**
   * Processes the next task to run, without checking if one exists
   */
  private def process(): Unit = {
    exclusiveLocked = true
    pendingRequest = Some(requestQueue.head.requestActor)

    schedule(requestQueue.dequeue() match {

      case _: GetTask =>
        log.info(tag + "Signalling read task")
        ReadTask(VALUE_EXTENSION)

      case post: PostTask =>
        log.info(tag + "Signalling write ahead task")
        WriteAheadTask(WRITE_AHEAD_EXTENSION, post.value)

      case _: DeleteTask =>
        log.info(tag + "Signalling tombstone task")
        TombstoneTask(VALUE_EXTENSION)
    })
  }

  /**
   * Executes the next task, if there is one
   */
  private def poll(): Unit = {
    log.debug(tag + "Polling next operation")
    if (requestQueue.isEmpty) suspend() else process()
  }

  /**
   * Completes the pending request by sending the result back
   *
   * @param result the request result
   */
  private def complete(result: ResultType): Unit = {
    pendingRequest match {
      case Some(requestPath) => actorContext.actorSelection(requestPath) ! result
      case None => log.error("Request complete called when no requests are pending")
    }
  }


  override def receive: Receive = {

    case task: PersistenceTask =>
      log.info(tag + s"Operation request received")
      requestQueue.enqueue(task)
      if (!exclusiveLocked) process()

    case ReadCompleteSignal(result) =>
      log.debug(tag + "Read complete signal received")
      complete(result.map(Some(_)))
      poll()

    case WriteCompleteSignal(result) =>
      log.debug(tag + "Write complete signal received")
      complete(result.map(_ => None))
      poll()

    case WriteAheadCommitSignal() =>
      log.debug(tag + "Write ahead commit signal received")
      schedule(WriteTransferTask(
        WRITE_AHEAD_EXTENSION,
        VALUE_EXTENSION
      ))

    case WriteAheadFailureSignal(e) =>
      log.debug(tag + "Write ahead failure signal received")
      complete(Failure(e))
      poll()

    case x => log.error(receivedUnknown(x))

  }
}