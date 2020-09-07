package schema

import java.nio.charset.StandardCharsets

import com.google.protobuf.ByteString
import com.risksense.ipaddr.IpAddress
import io.jvm.uuid._

import scala.language.implicitConversions


object ImplicitGrpcConversions {

  // String & Array[Byte]
  implicit def stringToByteArray(value: String): Array[Byte] =
    value.getBytes(StandardCharsets.UTF_8)

  implicit def byteArrayToString(bytes: Array[Byte]): String =
    new String(bytes, StandardCharsets.UTF_8)

  // String & ByteString
  implicit def stringToByteString(value: String): ByteString =
    ByteString.copyFrom(value)

  implicit def byteStringToString(bytes: ByteString): String =
    bytes.toStringUtf8

  // Array[Byte] & ByteString
  implicit def byteArrayToByteString(value: Array[Byte]): ByteString =
    ByteString.copyFrom(value)

  implicit def byteStringToByteArray(value: ByteString): Array[Byte] =
    value.toByteArray

  // UUID & ByteString
  implicit def UUIDToByteString(value: UUID): ByteString =
    ByteString.copyFrom(value.byteArray)

  implicit def byteStringToUUID(value: ByteString): UUID =
    UUID(value.toByteArray)
}


object ImplicitDataConversions {

  implicit def ipToInt(ipAddress: IpAddress): Int =
    ipAddress.key._1.toInt

  implicit def intToIp(ipAddress: Int): IpAddress =
    IpAddress(ipAddress.toLong)

  implicit def ipToString(ipAddress: IpAddress): String =
    ipAddress.toString

  implicit def stringToIp(ipAddress: String): IpAddress =
    IpAddress(ipAddress)
}