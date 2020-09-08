package replication.eventlog

import replication.eventlog.ReplicatedLog.Offset


object ReplicatedLog {

  type Offset = Int
}


trait ReplicatedLog {

  def apply(index: Int): Array[Byte]
  final def get(index: Int): Array[Byte] = apply(index)

  def append(term: Long, entry: Array[Byte]): Unit
  final def +=(term: Long, entry: Array[Byte]): Unit = append(term, entry)

  def slice(from: Int, until: Int): Array[Byte]
  def size: Offset

  def rollback(newSize: Int): Unit

  def lastLogTerm(): Long
  def lastLogIndex(): Int

  def offsetOf(index: Int): Offset
  def lengthOf(index: Int): Offset
  def termOf(index: Int): Long
}
