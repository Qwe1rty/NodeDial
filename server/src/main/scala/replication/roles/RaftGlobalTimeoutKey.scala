package replication.roles


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
 * The FSM timers use a string as a key, so this defines what the key is
 */
trait RaftGlobalTimeoutName {
  val TIMER_NAME = "raftGlobalTimer"
}