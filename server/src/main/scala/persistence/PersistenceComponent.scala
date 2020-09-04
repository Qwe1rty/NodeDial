package persistence

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import better.files.File
import common.ServerConstants
import administration.Administration.{AdministrationMessage, DeclareReadiness}
import persistence.PersistenceComponent.PersistenceTask
import persistence.execution.PartitionedTaskExecutor
import persistence.io.KeyStateManager
import persistence.io.KeyStateManager.KeyStateAction

import scala.concurrent.{Future, Promise}


class PersistenceComponent(override protected val context: ActorContext[PersistenceTask], membershipActor: ActorRef[AdministrationMessage])
  extends AbstractBehavior[PersistenceTask](context) {

  import PersistenceComponent._

  private val threadPartitionActor = context.spawn(PartitionedTaskExecutor(), "threadPartitionActor")
  private var keyMapping = Map[String, ActorRef[KeyStateAction]]()

  PERSISTENCE_DIRECTORY.createDirectoryIfNotExists()
  context.log.info(s"Directory ${PERSISTENCE_DIRECTORY.toString()} opened")

  membershipActor ! DeclareReadiness
  context.log.info("Persistence component initialized")


  override def onMessage(task: PersistenceTask): Behavior[PersistenceTask] = {
    context.log.info(s"Persistence task with hash ${task.keyHash} and request actor path ${task.requestPromise} received")

    if (!(keyMapping isDefinedAt task.keyHash)) {
      keyMapping += task.keyHash -> context.spawn(
        KeyStateManager(threadPartitionActor, task.keyHash),
        s"keyStateActor-${task.keyHash}"
      )
      context.log.debug(s"No existing state actor found for hash ${task.keyHash} - creating new state actor")
    }

    keyMapping(task.keyHash) ! Left(task)
    this
  }

}

object PersistenceComponent {

  type PersistenceData = Option[Array[Byte]]
  type PersistenceFuture = Future[PersistenceData]

  val PERSISTENCE_DIRECTORY: File = ServerConstants.BASE_DIRECTORY/"data"

  def apply(membershipActor: ActorRef[AdministrationMessage]): Behavior[PersistenceTask] =
    Behaviors.setup(new PersistenceComponent(_, membershipActor))


  /** Actor protocol: defines the set of tasks the persistence layer will accept */
  sealed trait PersistenceTask {
    val requestPromise: Promise[PersistenceData]
    val keyHash: String
  }

  /**
   * A get request
   *
   * @param requestPromise the actor to send the result back to, if there is one
   * @param keyHash the key hash
   */
  case class GetTask(
    requestPromise: Promise[PersistenceData],
    keyHash: String
  ) extends PersistenceTask

  /**
   * A write request
   *
   * @param requestPromise the actor to send the result back to, if there is one
   * @param keyHash the key hash
   * @param value the value to write the value as
   */
  case class WriteTask(
    requestPromise: Promise[PersistenceData],
    keyHash: String,
    value: Array[Byte]
  ) extends PersistenceTask

  /**
   * A delete request, which will be interpreted as a "tombstone" action
   *
   * @param requestPromise the actor to send the result back to, if there is one
   * @param keyHash the key hash
   */
  case class DeleteTask(
    requestPromise: Promise[PersistenceData],
    keyHash: String
  ) extends PersistenceTask
}