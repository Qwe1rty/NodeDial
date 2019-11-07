package persistence

import java.nio.file.Paths

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Sink}
import akka.util.ByteString
import server.service.{DeleteRequest, GetRequest, PostRequest}

import scala.concurrent.ExecutionContext
import server.datatypes.{OperationPackage, RequestTrait}

import scala.collection.mutable
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

  implicit private val ec: ExecutionContext = executor

  private val path = PersistenceActor.DIRECTORY_NAME.resolve(hash)

  private var requestQueue = mutable.Queue[OperationPackage]()
  private var exclusiveLocked = false // TODO make this a 2PL
  private var pendingRequest: Option[ActorRef] = None


  private def scheduleOperation(operation: OperationPackage): Unit = {
    {
      operation.requestBody match {
        case GetRequest(_) => ReadRequestTask(path)
        case PostRequest(_, value) => WriteAheadTask(path, ByteString(value.toByteArray))
        case DeleteRequest(_) => TombstoneRequestTask(path)
      }
    }.schedule(self)
  }

  private def suspend(): Unit = {
    exclusiveLocked = false
    pendingRequest = None
  }

  private def signal(): Unit = {
    exclusiveLocked = true
    pendingRequest = Some(requestQueue.head requestActor)
    scheduleOperation(requestQueue dequeue)
  }

  private def poll(): Unit = {
    if (requestQueue nonEmpty) signal()
    else suspend()
  }

  override def receive: Receive = {

    case operationRequest: OperationPackage => {
      if (exclusiveLocked) {
        requestQueue.enqueue(operationRequest)
      }
      else signal()
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

      if (requestQueue nonEmpty) scheduleOperation(requestQueue dequeue)
    }

    case _ => ??? // TODO log error

  }
}