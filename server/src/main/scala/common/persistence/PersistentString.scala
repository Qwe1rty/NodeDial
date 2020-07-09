package common.persistence

import better.files.File
import schema.ImplicitGrpcConversions._


object PersistentString {

  def apply(file: File): PersistentString = {
    new PersistentString(file)
  }
}


class PersistentString(file: File) extends PersistentVal[String](file) {

  /**
   * The function for serializing value to bytes
   */
  override protected def serialize: Function[String, Array[Byte]] = stringToByteArray

  /**
   * The function for deserialize bytes to its value
   */
  override protected def deserialize: Function[Array[Byte], String] = byteArrayToString
}
