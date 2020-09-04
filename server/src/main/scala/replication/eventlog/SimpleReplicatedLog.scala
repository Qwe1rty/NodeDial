package replication.eventlog

import java.io.RandomAccessFile

import better.files.File
import common.persistence.JavaSerializer
import replication.eventlog.ReplicatedLog.Offset

import scala.collection.mutable
import scala.util.{Failure, Success}


class SimpleReplicatedLog(
    private val indexFile: File,
    private val dataFile: File
  )
  extends ReplicatedLog {

  import SimpleReplicatedLog._

  indexFile.createFileIfNotExists(createParents = true)
  dataFile.createFileIfNotExists(createParents = true)

  private val dataAccess: RandomAccessFile = {
    dataFile.createFileIfNotExists()
    dataFile.newRandomAccess(File.RandomAccessMode.readWriteContentSynchronous)
  }

  private val metadata: LogMetadata = {
    if (indexFile.exists) LogMetadata.deserialize(indexFile.loadBytes) match {
      case Success(metadata)  => metadata
      case Failure(exception) => throw exception
    }
    else {
      val newMetadata = LogMetadata(INIT_LOG_INDEX)
      saveMetadata(newMetadata)
      newMetadata
    }
  }


  override def apply(index: Int): Array[Byte] = {
    val entry = new Array[Byte](lengthOf(index))
    dataAccess.read(entry, offsetOf(index), lengthOf(index))
    entry
  }

  // Note: important that metadata is updated AFTER the data itself, to prevent
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

  override def slice(from: Int, until: Int): Array[Byte] = {
    if (from >= until) {
      throw new IllegalArgumentException("Range is invalid: left bound must strictly be smaller than right bound")
    }

    val sliceLength =
      metadata.offsetIndex(until - 1).length +
      metadata.offsetIndex(until - 1).offset -
      metadata.offsetIndex(from).offset

    val entry = new Array[Byte](sliceLength)
    dataAccess.read(entry, metadata.offsetIndex(from).offset, sliceLength)
    entry
  }

  override def size(): Offset =
    metadata.offsetIndex.size

  override def rollback(newSize: Offset): Unit = {
    if (newSize < 0 || newSize > size()) {
      throw new IllegalArgumentException(s"Illegal new size: $newSize")
    }

    metadata.offsetIndex.trimEnd(size() - newSize)
    metadata.lastIncludedTerm = termOf(lastLogIndex())

    saveMetadata(metadata)
  }


  override def lastLogTerm(): Long =
    metadata.lastIncludedTerm

  override def lastLogIndex(): Int =
    size() - 1

  override def offsetOf(index: Int): Offset =
    metadata.offsetIndex(index).offset

  override def lengthOf(index: Int): Offset =
    metadata.offsetIndex(index).length

  override def termOf(index: Int): Long =
    metadata.offsetIndex(index).term


  private def saveMetadata(metadata: LogMetadata): Unit = {
    LogMetadata.serialize(metadata) match {
      case Success(bytes)     => indexFile.writeByteArray(bytes)
      case Failure(exception) => throw exception
    }
  }

}

private object SimpleReplicatedLog {

  private object LogMetadata extends JavaSerializer[LogMetadata] {

    def apply(elems: LogIndex*): LogMetadata =
      new LogMetadata(0, mutable.ListBuffer[LogIndex](elems: _*))
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

  private val INIT_LOG_INDEX: LogIndex = LogIndex(0, 0, 0)
}
