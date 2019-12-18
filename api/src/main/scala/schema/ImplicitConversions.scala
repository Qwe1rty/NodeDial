package schema

import java.nio.charset.StandardCharsets

import com.google.protobuf.ByteString

import scala.language.implicitConversions


object ImplicitGrpcConversions {

  implicit def string2ByteArray(value: String): Array[Byte] =
    value.getBytes(StandardCharsets.UTF_8)

  implicit def byteArray2String(bytes: Array[Byte]): String =
    new String(bytes, StandardCharsets.UTF_8)

  implicit def string2ByteString(value: String): ByteString =
    ByteString.copyFrom(value)

  implicit def byteString2String(bytes: ByteString): String =
    bytes.toStringUtf8
}


object ImplicitDataConversions {

//  implicit def ipToInt(ipAddress: IpAddress):
}