package replication.state

import administration.Membership


/** Actor protocol for Raft timeouts */
private[replication] trait RaftTimeouts


/** For pattern matching */
private[replication] trait RaftTimeoutKey

/**
 * This is the global timeout key for the Raft FSM. It's what determines
 * around half of the state transitions, usually when stuff isn't happening
 * for a while
 */
private[replication] case object RaftGlobalTimeoutKey extends RaftTimeoutKey

/**
 * The Raft local timeout key controls the timeout between how often to
 * resend a request to a specific node
 *
 * @param nodeID the node that the individual timeout is referring to
 */
private[replication] case class RaftIndividualTimeoutKey(nodeID: String) extends RaftTimeoutKey


/** For pattern matching */
private[replication] trait RaftTimeoutTick

/**
 * This is the global tick object that's send to the actor when the timeout
 * hits
 */
private[replication] case object RaftGlobalTimeoutTick extends RaftTimeoutTick

/**
 * @param nodeID the node that the individual timeout is referring to
 */
private[replication] case class RaftIndividualTimeoutTick(nodeID: String) extends RaftTimeoutTick