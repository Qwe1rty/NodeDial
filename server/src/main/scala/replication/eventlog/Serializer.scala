package replication.eventlog

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}

import scala.util.Try


object Serializer {

  type DefaultSerializer[T <: Serializable] = JavaSerializer[T]
}

trait Serializer[T] {

  def serialize(value: T): Try[Array[Byte]]
  def deserialize(bytes: Array[Byte]): Try[T]
}


trait JavaSerializer[T <: Serializable] extends Serializer[T] {

  override def serialize(value: T): Try[Array[Byte]] = Try {
    val bytes = new ByteArrayOutputStream()
    val obj = new ObjectOutputStream(bytes)
    obj.writeObject(value)
    obj.close()
    bytes.close()
    bytes.toByteArray
  }

  override def deserialize(bytes: Array[Byte]): Try[T] = Try {
    val bytes = new ByteArrayInputStream(bytes)
    val obj = new ObjectInputStream(bytes)
    val value = obj.readObject().asInstanceOf[T]
    bytes.close()
    obj.close()
    value
  }
}


trait ProtobufSerializer[T <: GeneratedMessage with Message[T]] extends Serializer[T] {

  // Reflection required to get the companion object...
  def messageCompanion: GeneratedMessageCompanion[T]

  override def serialize(value: T): Try[Array[Byte]] = Try {
    value.toByteArray
  }

  override def deserialize(bytes: Array[Byte]): Try[T] = Try {
    messageCompanion.parseFrom(bytes)
  }
}