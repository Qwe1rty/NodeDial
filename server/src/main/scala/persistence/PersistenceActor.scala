package persistence

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import better.files.File
import common.ServerConstants
import common.utils.DefaultActor
import membership.api.DeclareReadiness
import persistence.io.KeyStateActor
import service.OperationPackage


object PersistenceActor {

  val PERSISTENCE_DIRECTORY: File = ServerConstants.BASE_DIRECTORY/"data"


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
  extends DefaultActor
  with ActorLogging {

  private var keyMapping = Map[String, ActorRef]()

  PersistenceActor.PERSISTENCE_DIRECTORY.createDirectoryIfNotExists()
  log.info(s"Directory ${PersistenceActor.PERSISTENCE_DIRECTORY.toString()} opened")

  membershipActor ! DeclareReadiness
  log.info("Persistence actor initialized")


  override def receive: Receive = {

    case task: PersistenceTask =>
      log.info(s"Persistence task with hash ${task.keyHash} and request actor path ${task.requestActor} received")

      if (!(keyMapping isDefinedAt task.keyHash)) {
        keyMapping += task.keyHash -> KeyStateActor(executorActor, task.keyHash)
        log.debug(s"No existing state actor found for hash ${task.keyHash} - creating new state actor")
      }

      keyMapping(task.keyHash) ! task

    case x => log.error(receivedUnknown(x))
  }

}