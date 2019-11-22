package persistence.io

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import better.files.File
import server.datatypes.OperationPackage
import server.service.{DeleteRequest, GetRequest, PostRequest}

import scala.collection.mutable
import scala.util.Failure


object KeyStateActor {

  final private val WRITE_AHEAD_EXTENSION = ".wal"
  final private val VALUE_EXTENSION = ".val"


  def apply(executorActor: ActorRef, hash: String)(implicit actorContext: ActorContext): ActorRef =
    actorContext.actorOf(props(executorActor, hash), "keyStateActor")

  def props(executorActor: ActorRef, hash: String): Props =
    Props(new KeyStateActor(executorActor, hash))
}


class KeyStateActor(executorActor: ActorRef, hash: String) extends Actor with ActorLogging {

  private val requestQueue = mutable.Queue[OperationPackage]()
  private var exclusiveLocked = false // TODO make this a 2PL
  private var pendingRequest: Option[ActorRef] = None


  private def fileOf(extension: String): File =
    PersistenceActor.DIRECTORY_FILE/(hash + extension)

  private def schedule(task: IOTask): Unit =
    executorActor ! (hash, task)

  private def suspend(): Unit = {
    exclusiveLocked = false
    pendingRequest = None
  }

  private def signal(): Unit = {
    exclusiveLocked = true
    pendingRequest = Some(requestQueue.head.requestActor)
    schedule(requestQueue.dequeue().requestBody match {
      case GetRequest(_) =>
        ReadTask(fileOf(KeyStateActor.VALUE_EXTENSION))
      case PostRequest(_, value) =>
        WriteAheadTask(fileOf(KeyStateActor.WRITE_AHEAD_EXTENSION), value.toByteArray)
      case DeleteRequest(_) =>
        TombstoneTask(fileOf(KeyStateActor.VALUE_EXTENSION))
    })
  }

  private def poll(): Unit =
    if (requestQueue.isEmpty) suspend() else signal()

  override def receive: Receive = {

    case operationRequest: OperationPackage => {
      requestQueue.enqueue(operationRequest)
      if (!exclusiveLocked) signal()
    }

    case ReadCompleteSignal(result) => {
      pendingRequest.get ! result.map(_ => Some(_))
      poll()
    }

    case WriteCompleteSignal(result) => {
      pendingRequest.get ! result.map(_ => None)
      poll()
    }

    case WriteAheadCommitSignal() => {
      schedule(WriteTransferTask(
        fileOf(KeyStateActor.WRITE_AHEAD_EXTENSION),
        fileOf(KeyStateActor.VALUE_EXTENSION)
      ))
    }

    case WriteAheadFailureSignal(e) => {
      pendingRequest.get ! Failure(e)
      poll()
    }

    case _ => ??? // TODO log error

  }
}