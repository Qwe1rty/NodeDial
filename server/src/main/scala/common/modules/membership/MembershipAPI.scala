package common.modules.membership

import akka.actor.ActorRef
import com.risksense.ipaddr.IpAddress


// A named tuple that contains the node ID and IP address
case class Membership private(nodeID: String, ipAddress: IpAddress) {

  override def toString: String = s"[${nodeID}, ${ipAddress}]"
}


object MembershipAPI {

  case class GetClusterSize()

  /**
   * Requests a random node of the specified node state. Returns an `Option[MembershipPair]`
   * object, which will be equal to `None` if there are no other nodes in the cluster
   *
   * @param nodeState the state that the random node will be drawn from
   */
  case class GetRandomNode(nodeState: NodeState = NodeState.ALIVE)

  /**
   * Requests multiple random nodes of the specified node state. Returns a `Seq[MembershipPair]`
   * object, which will contain `number` elements.
   *
   * (Unless there are fewer other nodes in the cluster, then the `Seq[MembershipPair]` object
   * may contain less elements)
   *
   * @param number requested number of other random nodes
   * @param nodeState the state that the random nodes will be drawn from
   */
  case class GetRandomNodes(number: Int, nodeState: NodeState = NodeState.ALIVE)


  /**
   * Signals the membership actor to broadcast the declaration across to the other nodes
   *
   * @param nodeState state of the node
   * @param membershipPair node identifier
   */
  case class DeclareEvent(nodeState: NodeState, membershipPair: Membership)

  /**
   * Signals the membership actor to update its internal information with the reported event,
   * as well as publish the event to any subscribers
   *
   * @param nodeState state of the node
   * @param membershipPair node identifier
   */
  case class ReportEvent(nodeState: NodeState, membershipPair: Membership)


  case class Subscribe()(implicit actorRef: ActorRef)
  case class Unsubscribe()(implicit actorRef: ActorRef)
}
