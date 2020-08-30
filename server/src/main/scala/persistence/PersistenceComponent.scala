package persistence

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import better.files.File
import common.{DefaultActor, ServerConstants}
import membership.api.DeclareReadiness
import persistence.PersistenceComponent.{PERSISTENCE_DIRECTORY, PersistenceData}
import persistence.io.KeyStateActor.KeyTask
import persistence.io.{KeyStateActor}
import persistence.threading.ThreadPartitionActor

import scala.concurrent.{Future, Promise}


object PersistenceComponent {

  type PersistenceData = Option[Array[Byte]]

  val PERSISTENCE_DIRECTORY: File = ServerConstants.BASE_DIRECTORY/"data"
}

class PersistenceComponent(membershipActor: ActorRef)(implicit actorSystem: ActorSystem) {

  import PersistenceComponent._
  import common.ServerDefaults.INTERNAL_REQUEST_TIMEOUT

  private val threadPartitionActor = ThreadPartitionActor()
  private val persistenceActor = PersistenceActor(threadPartitionActor, membershipActor)


  def submitTask(task: PersistenceTask): Future[PersistenceData] = {
    (persistenceActor ? task)
      .mapTo[Future[PersistenceData]]
      .flatten
  }
}


object PersistenceActor {

  def apply(executorActor: ActorRef, membershipActor: ActorRef)(implicit actorSystem: ActorSystem): ActorRef = actorSystem.actorOf(
    Props(new PersistenceActor(executorActor, membershipActor)),
    "persistenceActor"
  )
}

class PersistenceActor private(executorActor: ActorRef, membershipActor: ActorRef) extends DefaultActor with ActorLogging {

  private var keyMapping = Map[String, ActorRef]()

  PERSISTENCE_DIRECTORY.createDirectoryIfNotExists()
  log.info(s"Directory ${PERSISTENCE_DIRECTORY.toString()} opened")

  membershipActor ! DeclareReadiness
  log.info("Persistence actor initialized")


  override def receive: Receive = {

    case task: PersistenceTask =>
      log.info(s"Persistence task with hash ${task.keyHash} and request actor path ${task.requestActor} received")

      if (!(keyMapping isDefinedAt task.keyHash)) {
        keyMapping += task.keyHash -> KeyStateActor(executorActor, task.keyHash)
        log.debug(s"No existing state actor found for hash ${task.keyHash} - creating new state actor")
      }

      val requestPromise = Promise[PersistenceData]()
      keyMapping(task.keyHash) ! KeyTask(task, requestPromise)
      sender ! requestPromise.future

    case x => log.error(receivedUnknown(x))
  }
}

