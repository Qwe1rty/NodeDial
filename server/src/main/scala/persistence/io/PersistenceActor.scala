package persistence.io

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import better.files.File
import common.utils.ActorDefaults
import service.OperationPackage


object PersistenceActor {

  val DIRECTORY_FILE: File = File.currentWorkingDirectory/".chordial"


  private def props(executorActor: ActorRef): Props =
    Props(new PersistenceActor(executorActor))

  def apply(executorActor: ActorRef)(implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(props(executorActor), "persistenceActor")
}


class PersistenceActor(executorActor: ActorRef) extends Actor with ActorLogging with ActorDefaults {

  private var keyMapping = Map[String, ActorRef]()

  PersistenceActor.DIRECTORY_FILE.createDirectoryIfNotExists()
  log.info(s"Directory ${PersistenceActor.DIRECTORY_FILE.toString()} opened")


  override def receive: Receive = {

    case operation: OperationPackage => {

      log.info(s"Operation with hash ${operation.requestHash} received by persistence")

      if (!(keyMapping isDefinedAt operation.requestHash)) {
        keyMapping += operation.requestHash -> KeyStateActor(executorActor, operation.requestHash)
        log.debug(s"No existing state actor found for hash ${operation.requestHash} - creating new state actor")
      }
      keyMapping(operation.requestHash) ! operation
    }

    case x => log.error(receivedUnknown(x))

  }
}