package common.modules.membership

import com.risksense.ipaddr.IpAddress


private[membership] object MembershipTable {

  def apply(): MembershipTable = new MembershipTable()
}


private[membership] class MembershipTable private() extends Map[String, NodeInfo] { self =>

  // Getters (Individual)
  override def apply(nodeID: String): NodeInfo = ???

  override def get(nodeID: String): Option[NodeInfo] = ???

  def address(nodeID: String): Option[IpAddress] = ???

  def version(nodeID: String): Option[Int] = ???

  // Getters (Aggregate)
  def states(nodeState: NodeState): Set[String] = ???

  // Modifiers
  override def +[V1 >: NodeInfo](entry: (String, V1)): Map[String, V1] = ???

  def +[V1 >: NodeInfo](nodeInfo: V1): Map[String, V1] = ???

  override def -(nodeID: String): MembershipTable = ???

  override def updated[V1 >: NodeInfo](nodeID: String, nodeInfo: V1): Map[String, V1] = ???

  def updated(nodeID: String, nodeState: NodeState): MembershipTable = ???

  def updated(nodeID: String, version: Int): MembershipTable = ???

  def updated(nodeInfo: NodeInfo): MembershipTable = ???

  def increment(nodeID: String): MembershipTable = ???

  // Other
  override def iterator: Iterator[(String, NodeInfo)] = ???
}
