package persistence


/**
 * Defines the set of tasks the persistence layer will accept
 */
sealed trait PersistenceTask {
  val requestActor: Option[ActorPath]
  val keyHash: String
}

/**
 * A get request
 *
 * @param requestActor the actor to send the result back to, if there is one
 * @param keyHash the key hash
 */
case class GetTask(
  requestActor: Option[ActorPath],
  keyHash: String
) extends PersistenceTask

/**
 * A write request
 *
 * @param requestActor the actor to send the result back to, if there is one
 * @param keyHash the key hash
 * @param value the value to write the value as
 */
case class PostTask(
  requestActor: Option[ActorPath],
  keyHash: String,
  value: Array[Byte]
) extends PersistenceTask

/**
 * A delete request, which will be interpreted as a "tombstone" action
 *
 * @param requestActor the actor to send the result back to, if there is one
 * @param keyHash the key hash
 */
case class DeleteTask(
  requestActor: Option[ActorPath],
  keyHash: String
) extends PersistenceTask