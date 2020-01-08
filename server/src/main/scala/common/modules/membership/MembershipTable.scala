package common.modules.membership

import com.risksense.ipaddr.IpAddress
import schema.ImplicitDataConversions._


/**
 * Represents the membership table, that contains all node information known
 * by any given node.
 */


private[membership] object MembershipTable {

  def apply(): MembershipTable = new MembershipTable()
}


private[membership] class MembershipTable private() extends Map[String, NodeInfo] { self =>

  private val stateGroups = Map[NodeState, Set[String]]()


  // Getters (Individual)
  override def apply(nodeID: String): NodeInfo =
    super.apply(nodeID)

  override def get(nodeID: String): Option[NodeInfo] =
    super.get(nodeID)

  def address(nodeID: String): IpAddress =
    this(nodeID).ipAddress

  def version(nodeID: String): Int =
    this(nodeID).version


  // Getters (Aggregated)
  def states(nodeState: NodeState): Set[String] =
    stateGroups(nodeState)

  def random(nodeState: NodeState, quantity: Int = 1): Seq[String] = ???


  // Modifiers
  override def +[V1 >: NodeInfo](entry: (String, V1)): Map[String, V1] = ???

  def +[V1 >: NodeInfo](nodeInfo: V1): MembershipTable = ???

  override def -(nodeID: String): MembershipTable = ???

  override def updated[V1 >: NodeInfo](nodeID: String, nodeInfo: V1): Map[String, V1] = ???

  def updated(nodeID: String, nodeState: NodeState): MembershipTable = ???

  def updated(nodeID: String, version: Int): MembershipTable = ???

  def updated(nodeInfo: NodeInfo): MembershipTable = ???

  def increment(nodeID: String): MembershipTable = ???


  // Other
  override def iterator: Iterator[(String, NodeInfo)] =
    super.iterator
}
