package partitioning


object PartitionRing {

  def apply(): PartitionRing = new PartitionRing
}


class PartitionRing {

  def add(ipAddress: Int, hashList: Seq[String]): Unit = ???

  def remove(ipAddress: Int): Unit = ???

  def lookup(hash: String): Int = ???
}
