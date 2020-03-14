package membership.api

import common.membership.types.NodeState


// Membership information calls
sealed trait InformationCall

/**
 * Asks the membership actor whether or not the node is ready to receive client requests
 * Returns a `Boolean` value
 */
case object GetReadiness extends InformationCall

/**
 * Get the current size of the cluster.
 * Returns an `Int` value
 */
case object GetClusterSize extends InformationCall

/**
 * Get the full set of cluster information.
 * Returns a `Seq[NodeInfo]`
 */
case object GetClusterInfo extends InformationCall

/**
 * Requests a random node of the specified node state.
 * Returns an `Option[Membership]` object, which will be equal to `None` if there are
 * no other nodes in the cluster
 *
 * @param nodeState the state that the random node will be drawn from
 */
case class GetRandomNode(nodeState: NodeState = NodeState.ALIVE) extends InformationCall

/**
 * Requests multiple random nodes of the specified node state.
 * Returns a `Set[Membership]` object, which will contain a max of `number` elements.
 *
 * (Unless there are fewer other nodes in the cluster, then the `Seq[Membership]` object
 * may contain less elements)
 *
 * @param number requested number of other random nodes
 * @param nodeState the state that the random nodes will be drawn from
 */
case class GetRandomNodes(nodeState: NodeState = NodeState.ALIVE, number: Int = 1) extends InformationCall

