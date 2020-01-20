package membership

import com.risksense.ipaddr.IpAddress
import common.membership.types.{NodeInfo, NodeState}
import schema.ImplicitDataConversions._

import scala.collection.immutable.MapLike
import scala.util.Random


/**
 * Represents the membership table, that contains all node information known
 * by any given node.
 */


private[membership] object MembershipTable {

  def apply(): MembershipTable = new MembershipTable(
    Map[NodeState, Set[String]](),
    Map[String, NodeInfo]()
  )
}


private[membership] class MembershipTable private(
    val stateGroups: Map[NodeState, Set[String]],
    val table: Map[String, NodeInfo]
  )
  extends Map[String, NodeInfo]
  with MapLike[String, NodeInfo, MembershipTable] { self =>


  // Getters (Individual)
  override def apply(nodeID: String): NodeInfo =
    table.apply(nodeID)

  override def get(nodeID: String): Option[NodeInfo] =
    table.get(nodeID)

  def address(nodeID: String): IpAddress =
    self(nodeID).ipAddress

  def version(nodeID: String): Int =
    self(nodeID).version

  def state(nodeID: String): NodeState =
    self(nodeID).state

  def membership(nodeID: String): Membership =
    Membership(nodeID, address(nodeID))


  // Getters (Aggregated)
  def states(nodeState: NodeState): Set[String] =
    stateGroups(nodeState)

  def random(nodeState: NodeState, quantity: Int = 1): Set[Membership] = stateGroups.get(nodeState) match {
    case Some(stateGroup) =>
      quantity match {
        case 1 => {
          val index = Random.nextInt(stateGroup.size)
          Set(stateGroup.view(index, index + 1).last).map(membership)
        }
        case n => Random.shuffle(stateGroup).take(n).map(membership)
      }
    case None => Set[Membership]()
  }


  // Modifiers
  def ++(that: MembershipTable): MembershipTable = new MembershipTable({
      (stateGroups.keySet ++ that.stateGroups.keySet)
        .map { state =>
          state -> (stateGroups.getOrElse(state, Set()) ++ that.stateGroups.getOrElse(state, Set()))
        }
        .toMap
    },
    table ++ that.table
  )

  def ++(that: Seq[NodeInfo]): MembershipTable = self ++ new MembershipTable({
      that
        .groupBy(_.state)
        .mapValues { nodeInfoSeq =>
          nodeInfoSeq
            .map(_.nodeId)
            .toSet
        }
    },
    that.map(nodeInfo => nodeInfo.nodeId -> nodeInfo).toMap
  )

  @deprecated
  override def +[V1 >: NodeInfo](entry: (String, V1)): Map[String, V1] =
    throw new UnsupportedOperationException("Cannot add upper bounded object to NodeInfo table")

  def +(entry: (String, NodeInfo)): MembershipTable =
    self + entry._2

  def +(nodeInfo: NodeInfo): MembershipTable = new MembershipTable({
      val nodeState = nodeInfo.state
      stateGroups + (nodeState -> (stateGroups.getOrElse(nodeState, Set[String]()) + nodeInfo.nodeId))
    },
    table + (nodeInfo.nodeId -> nodeInfo)
  )

  override def -(nodeID: String): MembershipTable = {
    if (self.contains(nodeID)) new MembershipTable({
        val nodeState = self.state(nodeID)
        stateGroups + (nodeState -> (stateGroups(nodeState) - nodeID))
      },
      table - nodeID
    )
    else self
  }

  def updated(nodeInfo: NodeInfo): MembershipTable =
    self + nodeInfo

  @deprecated
  override def updated[V1 >: NodeInfo](nodeID: String, nodeInfo: V1): Map[String, V1] =
    self + (nodeID -> nodeInfo)

  def updated(nodeID: String, nodeState: NodeState): MembershipTable = {
    if (self.contains(nodeID)) self.updated(NodeInfo(
      nodeID,
      self.address(nodeID),
      self.version(nodeID),
      nodeState
    ))
    else self
  }

  def updated(nodeID: String, version: Int): MembershipTable = {
    if (self.contains(nodeID)) self.updated(NodeInfo(
      nodeID,
      self.address(nodeID),
      version,
      self.state(nodeID)
    ))
    else self
  }

  def increment(nodeID: String): MembershipTable = {
    self.updated(
      nodeID,
      self
        .get(nodeID)
        .map(_.version)
        .getOrElse(1)
    )
  }


  // Other
  override def contains(nodeID: String): Boolean =
    table.contains(nodeID)

  override def iterator: Iterator[(String, NodeInfo)] =
    table.iterator

  override def empty: MembershipTable =
    MembershipTable()
}
