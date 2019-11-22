package persistence.io

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import better.files.File
import server.datatypes.OperationPackage


object PersistenceActor {

  final val DIRECTORY_FILE: File = File.currentWorkingDirectory/"chordial"


  def apply(executorActor: ActorRef)(implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(props(executorActor), "persistenceActor")

  def props(executorActor: ActorRef): Props =
    Props(new PersistenceActor(executorActor))
}


class PersistenceActor(executorActor: ActorRef) extends Actor with ActorLogging {

  private var keyMapping = Map[String, ActorRef]()

  PersistenceActor.DIRECTORY_FILE.createDirectoryIfNotExists()


  override def receive: Receive = {

    case operation: OperationPackage => {
      if (!(keyMapping isDefinedAt operation.requestHash)) {
        keyMapping += operation.requestHash -> KeyStateActor(executorActor, operation.requestHash)
      }
      keyMapping(operation.requestHash) ! operation.requestBody
    }

    case _ => ??? // TODO log error

  }
}