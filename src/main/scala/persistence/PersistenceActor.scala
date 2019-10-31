package persistence

import akka.actor.{Actor, ActorLogging, ActorRef}

class PersistenceActor extends Actor with ActorLogging {

  // TODO determine if actors need to complete a message before receving another
  //  [ANSWER: YES]

  // TODO figure out how to do asynchronous file read/write
  //  [ANSWER: YES but synchronous in-memory caching not possible if file ops are fully async?]

  private val keyTable = Map[String, ActorRef]()

  override def receive: Receive = {
    ???
  }
}