package common.persistence

import better.files.File

import scala.util.Try


object PersistentLong {

  def apply(file: File): PersistentLong = {
    new PersistentLong(file)
  }
}


class PersistentLong(file: File) extends PersistentVal[Long](file) {

  /**
   * Add number to value, and persist
   *
   * @param num number to add
   */
  def +=(num: Long): Unit = read().foreach(current => write(current + num))

  /**
   * Subtract number to value, and persist
   *
   * @param num number to add
   */
  def -=(num: Long): Unit = +=(-num)

  /**
   * Increment number by 1, and persist
   */
  def increment(): Unit = +=(1)

  /**
   * The function for serializing value to bytes
   */
  override protected def serialize(value: Long): Try[Array[Byte]] = Try {
    BigInt(value).toByteArray
  }

  /**
   * The function for deserialize bytes to its value
   */
  override protected def deserialize(bytes: Array[Byte]): Try[Long] = Try {
    BigInt(bytes).toLong
  }
}
