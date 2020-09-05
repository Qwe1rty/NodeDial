package common.persistence

import better.files.File

import scala.util.{Failure, Success, Try}


/**
 * PersistentVal is a mutable interface that is intended to make it easier
 * and more convenient to persist state, in a way that resembles working
 * with a regular variable.
 *
 * Interface is not thread-safe.
 *
 * @param file file where the value should be persisted
 * @tparam A the value type
 */
abstract class PersistentVal[A](val file: File) extends Serializer[A] {

  private var value: Option[A] = None

  // Read as separate step to avoid NullPointerExceptions
  value = read()


  /**
   * Persist a value to the file. Will completely overwrite file
   *
   * @param a the value to persist
   */
  final def write(a: A): Unit = {
    file.createFileIfNotExists()
    serialize(a) match {
      case Success(bytes) => file.writeByteArray(bytes)
      case Failure(exception) => throw exception
    }
    value = Some(a)
  }

  /**
   * Returns persisted value. If the value is loaded in memory, then no
   * disk seek will occur
   *
   * @return the persisted value
   */
  final def read(): Option[A] = {
    if (file.exists && value.isEmpty) deserialize(file.loadBytes) match {
      case Success(value) => this.value = Some(value)
      case Failure(exception) => throw exception
    }
    value
  }

  /**
   * Deletes the value from disk (and memory)
   */
  final def delete(): Unit = {
    file.delete()
    value = None
  }

  /**
   * Check if value exists
   */
  final def exists(): Boolean = file.exists
}
