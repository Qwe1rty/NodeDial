package membership

import com.risksense.ipaddr.IpAddress
import common.membership.types.{NodeInfo, NodeState}
import membership.api.Membership

import scala.collection.immutable.SetOps
import scala.collection.{StrictOptimizedSetOps, immutable, mutable}
import schema.ImplicitDataConversions._

import scala.util.Random


/**
 * Represents the membership table, that contains all node information known
 * by any given node.
 */

private[membership] object MembershipTable {

  final class NodeNotRegisteredException(nodeID: String) extends IllegalArgumentException(
    s"Node ID ${nodeID} is not registered"
  )

  def empty(): MembershipTable =
    new MembershipTable(Map[String, NodeInfo](), Map[NodeState, Set[String]]())

  def apply(entries: NodeInfo*): MembershipTable =
    fromSeq(entries)

  def apply(entries: Set[NodeInfo]): MembershipTable =
    fromSet(entries)

  def fromSet(entries: collection.Set[NodeInfo]): MembershipTable =
    fromSeq(entries.toSeq)

  def fromSeq(entries: collection.Seq[NodeInfo]): MembershipTable = {
    val table = entries
      .map(entry => (entry.nodeId, entry))
      .toMap
    val stateGroups = entries
      .groupBy(_.state)
      .view
      .mapValues(nodeInfoSeq => nodeInfoSeq.map(_.nodeId).toSet)
      .toMap
    new MembershipTable(table, stateGroups)
  }

}


final private[membership] class MembershipTable private(
    private val table:       Map[String, NodeInfo],
    private val stateGroups: Map[NodeState, Set[String]],
  )
  extends Set[NodeInfo]
  with SetOps[NodeInfo, immutable.Set, MembershipTable]
  with StrictOptimizedSetOps[NodeInfo, immutable.Set, MembershipTable] { self =>

  import MembershipTable._


  // Base set ops
  override def incl(nodeInfo: NodeInfo): MembershipTable = {
    val stateGroup = stateGroups.getOrElse(nodeInfo.state, Set[String]())

    new MembershipTable(
      table + (nodeInfo.nodeId -> nodeInfo),
      stateGroups + (nodeInfo.state -> (stateGroup + nodeInfo.nodeId))
    )
  }

  def register(nodeID: String): MembershipTable =
    if (contains(nodeID)) self + table(nodeID) else self

  override def excl(nodeInfo: NodeInfo): MembershipTable = {
    val stateGroup = stateGroups(nodeInfo.state)

    new MembershipTable(
      table - nodeInfo.nodeId,
      stateGroups + (nodeInfo.state -> (stateGroup - nodeInfo.nodeId)),
    )
  }

  def -(nodeID: String): MembershipTable =
    unregister(nodeID)

  def unregister(nodeID: String): MembershipTable =
    if (contains(nodeID)) self - table(nodeID) else self

  override def contains(elem: NodeInfo): Boolean =
    contains(elem.nodeId)

  def contains(nodeID: String): Boolean =
    table.contains(nodeID)

  override def empty: MembershipTable =
    MembershipTable.empty()

  override def concat(other: IterableOnce[NodeInfo]): MembershipTable =
    fromSpecific(iterator ++ other.iterator)


  // Derivative set ops
  def apply(nodeID: String): NodeInfo =
    table(nodeID)

  def get(nodeID: String): Option[NodeInfo] =
    table.get(nodeID)

  def addressOf(nodeID: String): IpAddress =
    table(nodeID).ipAddress

  def versionOf(nodeID: String): Int =
    table(nodeID).version

  def stateOf(nodeID: String): NodeState =
    table(nodeID).state

  def membershipOf(nodeID: String): Membership =
    Membership(nodeID, addressOf(nodeID))

  def incrementVersion(nodeID: String): MembershipTable =
    table.get(nodeID) match {
      case None => throw new NodeNotRegisteredException(nodeID)
      case Some(nodeInfo) =>
        new MembershipTable(
          table.updated(nodeID, nodeInfo.copy(version = nodeInfo.version + 1)),
          stateGroups
        )
    }

  def updateState(nodeID: String, newState: NodeState): MembershipTable =
    table.get(nodeID) match {
      case None => throw new NodeNotRegisteredException(nodeID)
      case Some(nodeInfo) =>
        self - nodeID
        self + nodeInfo.copy(state = newState)
    }


  // Aggregate ops
  def states(nodeState: NodeState): Set[String] =
    stateGroups(nodeState)

  def random(nodeState: NodeState, quantity: Int = 1): Set[Membership] =
    stateGroups.get(nodeState) match {
      case None => Set[Membership]()
      case Some(stateGroup) => quantity match {
        case n => Random.shuffle(stateGroup).take(n).map(membershipOf)
        case 1 =>
          val index = Random.nextInt(stateGroup.size)
          Set(stateGroup.view.slice(index, index + 1).last).map(membershipOf)
      }
    }


  // Iteration ops
  override protected def fromSpecific(coll: IterableOnce[NodeInfo]): MembershipTable =
    fromSeq(coll.iterator.toSeq)

  override protected def newSpecificBuilder: mutable.Builder[NodeInfo, MembershipTable] =
    iterableFactory.newBuilder[NodeInfo].mapResult(fromSet)

  override def iterator: Iterator[NodeInfo] =
    table.valuesIterator
}

