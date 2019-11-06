package persistence

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.ActorMaterializer
import server.service.{DeleteRequest, GetRequest, PostRequest}

import scala.concurrent.ExecutionContext

object KeyStateActor {

   def props
       (executor: ExecutionContext)
       (implicit materializer: ActorMaterializer): Props =
     Props(new KeyStateActor(executor))
}

class KeyStateActor
    (private val executor: ExecutionContext)
    (implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {

  implicit private val ec: ExecutionContext = executor

  private var pendingResponse = false

  override def receive: Receive = {

    case server.datatypes.RequestTrait => {

    }

    case GetRequest => ???
    case PostRequest => ???
    case DeleteRequest => ???
    case _ => ??? // TODO log error

  }
}