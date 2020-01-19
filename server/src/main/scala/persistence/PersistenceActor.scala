package persistence

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import better.files.File
import common.ChordialConstants
import common.utils.ActorDefaults
import membership.MembershipAPI
import persistence.io.KeyStateActor
import service.OperationPackage


object PersistenceActor {

  val PERSISTENCE_DIRECTORY: File = ChordialConstants.BASE_DIRECTORY/"data"


  def apply
      (executorActor: ActorRef, membershipActor: ActorRef)
      (implicit actorSystem: ActorSystem): ActorRef = {

    actorSystem.actorOf(
      Props(new PersistenceActor(executorActor, membershipActor)),
      "persistenceActor"
    )
  }

}


class PersistenceActor private(
    executorActor: ActorRef,
    membershipActor: ActorRef
  )
  extends Actor
  with ActorLogging
  with ActorDefaults {

  private var keyMapping = Map[String, ActorRef]()

  PersistenceActor.PERSISTENCE_DIRECTORY.createDirectoryIfNotExists()
  log.info(s"Directory ${PersistenceActor.PERSISTENCE_DIRECTORY.toString()} opened")

  membershipActor ! MembershipAPI.DeclareReadiness
  log.info("Persistence actor initialized")


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