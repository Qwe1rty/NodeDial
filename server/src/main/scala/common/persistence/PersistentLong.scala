package common.persistence

import better.files.File


object PersistentLong {

  def apply(file: File): PersistentLong = {
    new PersistentLong(file)
  }
}


class PersistentLong(file: File) extends PersistentVal[Long](file) {

  /**
   * The function for serializing value to bytes
   */
  override protected def serialize: Function[Long, Array[Byte]] = {
    long: Long => BigInt(long).toByteArray
  }

  /**
   * The function for deserialize bytes to its value
   */
  override protected def deserialize: Function[Array[Byte], Long] = {
    bytes: Array[Byte] => BigInt(bytes).toLong
  }

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
}
