package replication.roles

import membership.api.Membership


/**
 * This is the global timeout key for the Raft FSM. It's what determines
 * around half of the state transitions, usually when stuff isn't happening
 * for a while
 */
case object RaftGlobalTimeoutKey


/**
 * This is the global tick object that's send to the actor when the timeout
 * hits
 */
case object RaftGlobalTimeoutTick


/**
 * The FSM timers use a string as a timer key, so the default key is defined
 * here
 */
trait RaftGlobalTimeoutName {
  val TIMER_NAME = "raftGlobalTimer"
}


/**
 * The Raft local timeout key controls the timeout between how often to
 * resend a request to a specific node
 *
 * @param node the node that the request should be resent to
 */
case class RaftIndividualTimeoutKey(node: Membership)