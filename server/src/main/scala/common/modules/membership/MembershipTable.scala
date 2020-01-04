package common.modules.membership


private[membership] object MembershipTable {

  def apply(): MembershipTable = new MembershipTable
}


private[membership] class MembershipTable private() extends Map[String, NodeInfo] { self =>

  override def apply(nodeID: String): NodeInfo = ???

  override def +[V1 >: NodeInfo](entry: (String, V1)): Map[String, V1] = ???

  def +[V1 >: NodeInfo](nodeInfo: V1): Map[String, V1] = ???

  override def get(nodeID: String): Option[NodeInfo] = ???

  override def -(nodeID: String): Map[String, NodeInfo] = ???

  override def updated[V1 >: NodeInfo](nodeID: String, nodeInfo: V1): Map[String, V1] = ???

  override def iterator: Iterator[(String, NodeInfo)] = ???

  def states(nodeState: NodeState): Set[String] = ???

  def version(nodeID: String): Option[Int] = ???

}
