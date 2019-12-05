package schema

import java.nio.charset.StandardCharsets

import com.google.protobuf.ByteString

import scala.language.implicitConversions


object ImplicitConversions {

  implicit def encodeString(value: String): ByteString =
    ByteString.copyFrom(value.getBytes(StandardCharsets.UTF_8))

  implicit def decodeString(bytes: ByteString): String =
    bytes.toStringUtf8
}
