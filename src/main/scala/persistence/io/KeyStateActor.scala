package persistence.io

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.ActorMaterializer
import akka.util.ByteString
import server.datatypes.OperationPackage
import server.service.{DeleteRequest, GetRequest, PostRequest}

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


object KeyStateActor {

  def props
      (hash: String, executor: ExecutionContext)
      (implicit materializer: ActorMaterializer): Props =
    Props(new KeyStateActor(hash, executor))
}


class KeyStateActor
    (hash: String, executor: ExecutionContext)
    (implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {

  implicit final private val ec: ExecutionContext = executor

  private val path = PersistenceActor.DIRECTORY_NAME.resolve(hash)

  private val requestQueue = mutable.Queue[OperationPackage]()
  private var exclusiveLocked = false // TODO make this a 2PL
  private var pendingRequest: Option[ActorRef] = None


  private def suspend(): Unit = {
    exclusiveLocked = false
    pendingRequest = None
  }

  private def signal(): Unit = {
    exclusiveLocked = true
    pendingRequest = Some(requestQueue.head requestActor)
    (
      requestQueue.dequeue().requestBody match {
        case GetRequest(_) => ReadTaskTask(path)
        case PostRequest(_, value) => WriteAheadTask(path, ByteString(value.toByteArray))
        case DeleteRequest(_) => TombstoneTaskTask(path)
      }
    ).schedule(self)
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

        case ReadCommittedSignal(_) |
             WriteTransferCommittedSignal(_) |
             TombstoneCommittedSignal(_) => {
          pendingRequest.get ! signal.result
          poll()
        }

        case WriteAheadCommittedSignal(result) => {
          result match {
            case Success(_) => WriteTransferTask(path).schedule(self)
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