package replication.eventlog


trait ReplicatedLog {

  def lastLogTerm(): Long
  def lastLogIndex(): Int

  def append(term: Long, entry: Array[Byte]): Unit
  final def +=(term: Long, entry: Array[Byte]): Unit = append(term, entry)

  def apply(index: Int): Array[Byte]
  final def get(index: Int): Array[Byte] = apply(index)
}
