package replication.state

import java.util.concurrent.TimeUnit

import common.time.TimeRange
import membership.api.Membership

import scala.concurrent.duration.FiniteDuration


private[replication] trait RaftTimeouts {

  /**
   * The FSM timers use a string as a timer key, so the default election timer key name
   * is defined here
   */
  val ELECTION_TIMER_NAME = "raftGlobalTimer"

  val ELECTION_TIMEOUT_LOWER_BOUND: FiniteDuration = FiniteDuration(150, TimeUnit.MILLISECONDS)
  val ELECTION_TIMEOUT_UPPER_BOUND: FiniteDuration = FiniteDuration(325, TimeUnit.MILLISECONDS)

  val ELECTION_TIMEOUT_RANGE: TimeRange = TimeRange(ELECTION_TIMEOUT_LOWER_BOUND, ELECTION_TIMEOUT_UPPER_BOUND)
  val INDIVIDUAL_NODE_TIMEOUT: FiniteDuration = FiniteDuration(50, TimeUnit.MILLISECONDS)
  val NEW_LOG_ENTRY_TIMEOUT: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)
}


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
 * @param node the node that the individual timeout is referring to
 */
private[replication] case class RaftIndividualTimeoutKey(node: Membership) extends RaftTimeoutKey


/** For pattern matching */
private[replication] trait RaftTimeoutTick

/**
 * This is the global tick object that's send to the actor when the timeout
 * hits
 */
private[replication] case object RaftGlobalTimeoutTick extends RaftTimeoutTick

/**
 * @param node the node that the individual timeout is referring to
 */
private[replication] case class RaftIndividualTimeoutTick(node: Membership) extends RaftTimeoutTick