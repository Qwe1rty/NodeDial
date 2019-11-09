package persistence.io

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.ByteString
import server.datatypes.OperationPackage
import server.service.{DeleteRequest, GetRequest, PostRequest}

import scala.collection.mutable
import scala.util.{Failure, Success}


object KeyStateActor {

  def props(executorActor: ActorRef, hash: String): Props =
    Props(new KeyStateActor(executorActor, hash))
}


class KeyStateActor(executorActor: ActorRef, hash: String) extends Actor with ActorLogging {

  private val path = PersistenceActor.DIRECTORY_NAME.resolve(hash)

  private val requestQueue = mutable.Queue[OperationPackage]()
  private var exclusiveLocked = false // TODO make this a 2PL
  private var pendingRequest: Option[ActorRef] = None


  private def schedule(task: IOTask): Unit = {
    executorActor ! (hash, task)
  }

  private def suspend(): Unit = {
    exclusiveLocked = false
    pendingRequest = None
  }

  private def signal(): Unit = {
    exclusiveLocked = true
    pendingRequest = Some(requestQueue.head requestActor)
    schedule(requestQueue.dequeue().requestBody match {
      case GetRequest(_) => ReadTask(path)
      case PostRequest(_, value) => WriteAheadTask(path, ByteString(value.toByteArray))
      case DeleteRequest(_) => TombstoneTask(path)
    })
  }

  private def poll(): Unit = {
    if (requestQueue isEmpty) suspend() else signal()
  }

  override def receive: Receive = {

    case operationRequest: OperationPackage => {
      requestQueue.enqueue(operationRequest)
      if (!exclusiveLocked) signal()
    }

    case signal: IOSignal => {

      // TODO log operation under debug

      signal match {

        case ReadCommitSignal(_) |
             WriteTransferCommitSignal(_) |
             TombstoneCommitSignal(_) => {
          pendingRequest.get ! signal.result
          poll()
        }

        case WriteAheadCommitSignal(result) => {
          result match {
            case Success(_) => schedule(WriteTransferTask(path))
            case Failure(exception: Exception) => { // TODO handle this actually better
              pendingRequest.get ! Failure(exception)
              poll()
            }
          }
        }

      }
    }

    case _ => ??? // TODO log error

  }
}