package replication.eventlog

import java.io.RandomAccessFile

import better.files.File
import replication.eventlog.SimpleReplicatedLog.{LogIndex, LogMetadata, Offset}

import scala.collection.mutable
import scala.util.{Failure, Success}


private[this] object SimpleReplicatedLog {

  type Offset = Int


  private object LogMetadata extends JavaSerializer[LogMetadata] {

    def apply(): LogMetadata =
      new LogMetadata(0, mutable.Buffer[LogIndex]())
  }

  @SerialVersionUID(100L)
  private class LogMetadata(
    var lastIncludedTerm: Long,
    val offsetIndex: mutable.Buffer[LogIndex]
  )
  extends Serializable {

    def append(term: Long, logIndex: LogIndex): Unit = {
      if (term > lastIncludedTerm) lastIncludedTerm = term
      offsetIndex.addOne(logIndex)
    }
  }


  private object LogIndex extends JavaSerializer[LogIndex]

  private case class LogIndex(
    offset: Offset,
    length: Int,
    term: Long,
  )
}


class SimpleReplicatedLog(
    private val indexFile: File,
    private val dataFile: File
  )
  extends ReplicatedLog {

  dataFile.createFileIfNotExists()

  private val metadata: LogMetadata = loadMetadata()
  private val randomAccess: RandomAccessFile =
    dataFile.newRandomAccess(File.RandomAccessMode.readWriteContentSynchronous)


  override def lastLogTerm(): Long = metadata.lastIncludedTerm

  override def lastLogIndex(): Int = metadata.offsetIndex.length

  // Note: important that you update the metadata AFTER the data itself, to prevent
  // invalid state
  override def append(term: Long, entry: Array[Byte]): Unit = {
    val logIndex = LogIndex(dataFile.size.toInt, entry.length, term)

    LogIndex.serialize(logIndex) match {
      case Success(bytes) =>
        dataFile.appendByteArray(bytes)
        metadata.append(term, logIndex)
        saveMetadata(metadata)
      case Failure(exception) => throw exception
    }
  }

  override def apply(index: Int): Array[Byte] = {
    val entry = new Array[Byte](lengthOf(index))
    randomAccess.read(entry, offsetOf(index), lengthOf(index))
    entry
  }

  override def slice(from: Int, until: Int): Array[Byte] = {
    if (from >= until) {
      throw new IllegalArgumentException("Range is invalid: left bound must strictly be smaller than right bound")
    }

    val sliceLength =
      metadata.offsetIndex(until - 1).length +
      metadata.offsetIndex(until - 1).offset -
      metadata.offsetIndex(from).offset

    val entry = new Array[Byte](sliceLength)
    randomAccess.read(entry, metadata.offsetIndex(from).offset, sliceLength)
    entry
  }


  private def offsetOf(index: Int): Offset = metadata.offsetIndex(index).offset

  private def lengthOf(index: Int): Offset = metadata.offsetIndex(index).length

  private def loadMetadata(): LogMetadata = {
    if (indexFile.exists) LogMetadata.deserialize(indexFile.loadBytes) match {
      case Success(metadata)  => metadata
      case Failure(exception) => throw exception
    }
    else LogMetadata()
  }

  private def saveMetadata(metadata: LogMetadata): Unit = {
    LogMetadata.serialize(metadata) match {
      case Success(bytes)     => indexFile.writeByteArray(bytes)
      case Failure(exception) => throw exception
    }
  }

}
