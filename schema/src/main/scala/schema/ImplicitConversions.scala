package schema

import com.google.protobuf.ByteString

import scala.language.implicitConversions


object ImplicitConversions {

  implicit def encodeString(value: String): ByteString =
    ByteString.copyFrom(value.toCharArray.map(_.toByte))

  implicit def decodeString(bytes: ByteString): String =
    bytes.toString
}
