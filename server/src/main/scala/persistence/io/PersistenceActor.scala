package persistence.io

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import better.files.File
import common.ChordialConstants
import common.utils.ActorDefaults
import service.OperationPackage


object PersistenceActor {

  val PERSISTENCE_DIRECTORY: File = ChordialConstants.BASE_DIRECTORY/"data"


  def apply(executorActor: ActorRef)(implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(
      Props(new PersistenceActor(executorActor)),
      "persistenceActor"
    )
}


class PersistenceActor private(executorActor: ActorRef) extends Actor
                                                        with ActorLogging
                                                        with ActorDefaults {

  private var keyMapping = Map[String, ActorRef]()

  PersistenceActor.PERSISTENCE_DIRECTORY.createDirectoryIfNotExists()
  log.info(s"Directory ${PersistenceActor.PERSISTENCE_DIRECTORY.toString()} opened")


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