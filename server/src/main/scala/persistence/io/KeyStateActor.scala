package persistence.io

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import better.files.File
import common.utils.ActorDefaults
import schema.service.{DeleteRequest, GetRequest, PostRequest}
import service.OperationPackage

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.Failure


object KeyStateActor {

  private val WRITE_AHEAD_EXTENSION = ".wal"
  private val VALUE_EXTENSION = ".val"


  private def props(executorActor: ActorRef, hash: String): Props =
    Props(new KeyStateActor(executorActor, hash))

  def apply(executorActor: ActorRef, hash: String)(implicit actorContext: ActorContext): ActorRef =
    actorContext.actorOf(props(executorActor, hash), s"keyStateActor-${hash}")
}


class KeyStateActor(executorActor: ActorRef, hash: String) extends Actor
                                                           with ActorLogging
                                                           with ActorDefaults {
  import KeyStateActor._

  final private val tag = s"${hash} -> " // TODO patternize this

  private val requestQueue = mutable.Queue[OperationPackage]() // TODO make this immutable
  private var exclusiveLocked = false // TODO make this a 2PL
  private var pendingRequest: Option[ActorRef] = None


  implicit private def fileOf(extension: String): File =
    PersistenceActor.PERSISTENCE_DIRECTORY/(hash + extension)


  private def schedule(task: IOTask): Unit = {
    executorActor ! (hash, task)
    log.debug(tag + s"Submitting task to task scheduler")
  }

  private def suspend(): Unit = {
    exclusiveLocked = false
    pendingRequest = None
    log.debug(tag + "Suspending actor")
  }

  private def signal(): Unit = {
    exclusiveLocked = true
    pendingRequest = Some(requestQueue.head.requestActor)

    schedule(requestQueue.dequeue().requestBody match {

      case GetRequest(_) =>
        log.info(tag + "Signalling read task")
        ReadTask(VALUE_EXTENSION)

      case PostRequest(_, value) =>
        log.info(tag + "Signalling write ahead task")
        WriteAheadTask(WRITE_AHEAD_EXTENSION, value.toByteArray)

      case DeleteRequest(_) =>
        log.info(tag + "Signalling tombstone task")
        TombstoneTask(VALUE_EXTENSION)
    })
  }

  private def poll(): Unit = {
    log.debug(tag + "Polling next operation")
    if (requestQueue.isEmpty) suspend() else signal()
  }


  override def receive: Receive = {

    case operationRequest: OperationPackage => {
      log.info(tag + s"Operation request received")
      requestQueue.enqueue(operationRequest)
      if (!exclusiveLocked) signal()
    }

    case ReadCompleteSignal(result) => {
      log.debug(tag + "Read complete signal received")
      pendingRequest.get ! result.map(Some(_))
      poll()
    }

    case WriteCompleteSignal(result) => {
      log.debug(tag + "Write complete signal received")
      pendingRequest.get ! result.map(_ => None)
      poll()
    }

    case WriteAheadCommitSignal() => {
      log.debug(tag + "Write ahead commit signal received")
      schedule(WriteTransferTask(
        WRITE_AHEAD_EXTENSION,
        VALUE_EXTENSION
      ))
    }

    case WriteAheadFailureSignal(e) => {
      log.debug(tag + "Write ahead failure signal received")
      pendingRequest.get ! Failure(e)
      poll()
    }

    case x => log.error(receivedUnknown(x))

  }
}