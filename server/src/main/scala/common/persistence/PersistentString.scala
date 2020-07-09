package common.persistence

import better.files.File
import schema.ImplicitGrpcConversions._

import scala.util.Try


object PersistentString {

  def apply(file: File): PersistentString = {
    new PersistentString(file)
  }
}


class PersistentString(file: File) extends PersistentVal[String](file) {

  /**
   * The function for serializing value to bytes
   */
  override protected def serialize(value: String): Try[Array[Byte]] = Try {
    stringToByteArray(value)
  }

  /**
   * The function for deserialize bytes to its value
   */
  override protected def deserialize(bytes: Array[Byte]): Try[String] = Try {
    byteArrayToString(bytes)
  }
}
