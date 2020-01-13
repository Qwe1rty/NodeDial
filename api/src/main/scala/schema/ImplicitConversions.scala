package schema

import java.nio.charset.StandardCharsets

import com.google.protobuf.ByteString
import com.risksense.ipaddr.IpAddress

import scala.language.implicitConversions


object ImplicitGrpcConversions {

  implicit def stringToByteArray(value: String): Array[Byte] =
    value.getBytes(StandardCharsets.UTF_8)

  implicit def byteArrayToString(bytes: Array[Byte]): String =
    new String(bytes, StandardCharsets.UTF_8)

  implicit def stringToByteString(value: String): ByteString =
    ByteString.copyFrom(value)

  implicit def byteStringToString(bytes: ByteString): String =
    bytes.toStringUtf8
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