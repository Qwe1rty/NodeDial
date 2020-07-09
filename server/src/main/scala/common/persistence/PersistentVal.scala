package common.persistence

import better.files.File


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
abstract class PersistentVal[A] private[persistence](
    val file: File,
  ) { self =>

  private var value: Option[A] = read()

  /**
   * Persist a value to the file. Will completely overwrite file
   *
   * @param a the value to persist
   */
  final def write(a: A): Unit = {
    file.writeByteArray(serialize(a))
    value = Some(a)
  }

  /**
   * Returns persisted value. If the value is loaded in memory, then no
   * disk seek will occur
   *
   * @return the persisted value
   */
  final def read(): Option[A] = {
    if (value.isEmpty && file.exists) {
      value = Some(deserialize(file.loadBytes))
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


  /**
   * The function for serializing value to bytes
   */
  protected def serialize: Function[A, Array[Byte]]

  /**
   * The function for deserialize bytes to its value
   */
  protected def deserialize: Function[Array[Byte], A]
}
