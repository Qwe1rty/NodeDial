package persistence

import java.nio.file.Paths

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Sink}
import server.service.{DeleteRequest, GetRequest, PostRequest}

import scala.concurrent.ExecutionContext
import server.datatypes.{OperationPackage, RequestTrait}

import scala.collection.mutable


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


  override def receive: Receive = {

    case operationRequest: OperationPackage => {
      if (exclusiveLocked) {
        requestQueue.enqueue(operationRequest)
        ()
      }
      exclusiveLocked = true
      ReadRequestTask(path).schedule(self, )
    }

    case signal: IOSignal => {

      // TODO log operation under debug

      signal match {

        case ReadCommittedSignal => {
          if (requestQueue nonEmpty) {
            scheduleOperation(requestQueue dequeue)
          }
          else exclusiveLocked = false
        }

        case WriteAheadCommittedSignal => {

        }

        case WriteTransferCommittedSignal => {

        }
      }

      if (requestQueue nonEmpty) scheduleOperation(requestQueue dequeue)
    }

//    case ??? => ??? // TODO make enum for response signals

    case _ => ??? // TODO log error

  }
}