package common.persistence

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}

import scala.util.Try


/**
 * The interface that defines serialization and deserialization of any object
 *
 * @tparam T type of object to serialize/deserialize
 */
trait Serializer[T] {

  def serialize(value: T): Try[Array[Byte]]
  def deserialize(bytes: Array[Byte]): Try[T]
}

object Serializer {

  type DefaultSerializer[T <: Serializable] = JavaSerializer[T]
}


/**
 * This serializer implementation uses the built-in Java object serialization
 * method
 *
 * @tparam T type of object to serialize/deserialize
 */
trait JavaSerializer[T <: Serializable] extends Serializer[T] {

  override def serialize(value: T): Try[Array[Byte]] = Try {
    val bytes = new ByteArrayOutputStream()
    val obj = new ObjectOutputStream(bytes)
    obj.writeObject(value)
    obj.close()
    bytes.close()
    bytes.toByteArray
  }

  override def deserialize(serial: Array[Byte]): Try[T] = Try {
    val bytes = new ByteArrayInputStream(serial)
    val obj = new ObjectInputStream(bytes)
    val value = obj.readObject().asInstanceOf[T]
    bytes.close()
    obj.close()
    value
  }
}


/**
 * This serializer uses protobuf's serialization. The message companion needs
 * to be manually defined, as the companion object cannot be found during runtime
 *
 * Only works for ScalaPB Proto2 generated objects
 *
 * @tparam T type of object to serialize/deserialize
 */
trait ProtobufSerializer[T <: GeneratedMessage with Message[T]] extends Serializer[T] {

  // NOTE: type erasure combined with no trait parameters prevents the type
  // tag being passed in, so this must be defined by user.
  def messageCompanion: GeneratedMessageCompanion[T]


  override def serialize(value: T): Try[Array[Byte]] = Try {
    value.toByteArray
  }

  override def deserialize(serial: Array[Byte]): Try[T] = Try {
    messageCompanion.parseFrom(serial)
  }
}