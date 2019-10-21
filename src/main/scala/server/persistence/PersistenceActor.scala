package server.persistence

import akka.actor.Actor
import server.service.{DeleteRequest, GetRequest, PostRequest}

class PersistenceActor extends Actor {

//  val Map[]

  // TODO determine if actors need to complete a message before receving another [ANSWER: YES]
  // TODO figure out how to do an non blocking file read/write [ANSWER: ???]

  override def receive: Receive = {

//    val hash =
    case GetRequest => true
    case PostRequest => true
    case DeleteRequest => true
  }
}