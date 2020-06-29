package common.persistence

import better.files.File
import schema.ImplicitGrpcConversions._


class PersistentString(file: File) extends PersistentVal[String](file) {

  /**
   * The function for serializing value to bytes
   */
  override protected def encodeValue: Function[String, Array[Byte]] = stringToByteArray

  /**
   * The function for deserialize bytes to its value
   */
  override protected def decodeValue: Function[Array[Byte], String] = byteArrayToString
}
