package replication.eventlog

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import com.sun.tools.javac.code.TypeTag
import scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}

import scala.reflect.runtime.universe._
import scala.util.Try

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


trait ProtobufSerializer[T <: GeneratedMessage with Message[T]: TypeTag[T]] extends Serializer[T] {

  import scala.reflect.runtime.currentMirror

  // Reflection required to get the companion object...
  val messageCompanion: GeneratedMessageCompanion[T] = currentMirror
    .reflectModule(typeOf[T].typeSymbol.companion.asModule)
    .instance
    .asInstanceOf[GeneratedMessageCompanion[T]]

  override def serialize(value: T): Try[Array[Byte]] = Try {
    value.toByteArray
  }

  override def deserialize(bytes: Array[Byte]): Try[T] = Try {
    messageCompanion.parseFrom(bytes)
  }
}