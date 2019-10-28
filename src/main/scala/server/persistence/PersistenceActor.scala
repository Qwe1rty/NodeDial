package server.persistence

import akka.actor.{Actor, ActorRef}

class PersistenceActor extends Actor {

  private val keyTable = Map[String, ActorRef]()

  // TODO determine if actors need to complete a message before receving another
  //  [ANSWER: YES]

  // TODO figure out how to do an non-blocking file read/write
  //  [ANSWER: YES but synchronous in-memory caching not possible?]

  override def receive: Receive = {

//    case GetRequest(request) => request.
//    case PostRequest => true
//    case DeleteRequest => true

  }
}