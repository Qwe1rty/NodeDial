package replication.roles

import membership.api.Membership


/**
 * The FSM timers use a string as a timer key, so the default key is defined
 * here
 */
trait RaftGlobalTimeoutName {
  val TIMER_NAME = "raftGlobalTimer"
}


/** For pattern matching */
trait RaftTimeoutKey

/**
 * This is the global timeout key for the Raft FSM. It's what determines
 * around half of the state transitions, usually when stuff isn't happening
 * for a while
 */
case object RaftGlobalTimeoutKey extends RaftTimeoutKey


/**
 * The Raft local timeout key controls the timeout between how often to
 * resend a request to a specific node
 *
 * @param node the node that the individual timeout is referring to
 */
case class RaftIndividualTimeoutKey(node: Membership) extends RaftTimeoutKey


/** For pattern matching */
trait RaftTimeoutTick

/**
 * This is the global tick object that's send to the actor when the timeout
 * hits
 */
case object RaftGlobalTimeoutTick extends RaftTimeoutTick

/**
 * @param node the node that the individual timeout is referring to
 */
case class RaftIndividualTimeoutTick(node: Membership) extends RaftTimeoutTick