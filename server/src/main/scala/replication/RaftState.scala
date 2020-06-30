package replication

import common.ServerConstants
import common.persistence.PersistentString


/**
 * RaftState are the state variables that are used by the raft algorithm to track things
 * like election status, log entries, etc.
 *
 * Since some variables need to be persisted to disk, the class is inherently non-immutable
 * and therefore the RaftState class is defined as a partially mutable object
 */
object RaftState {

  private val RAFT_DIR             = ServerConstants.BASE_DIRECTORY/"raft"
  private val RAFT_STATE_EXTENSION = ".state"

  def apply(): RaftState = new RaftState
}


class RaftState() {

  import RaftState._

  val votedFor: PersistentString = PersistentString(RAFT_DIR/"votedFor"/RAFT_STATE_EXTENSION)

  val commitIndex: Long = 0
  val lastApplied: Long = 0

}
