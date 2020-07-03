package replication

import better.files.File
import common.ServerConstants
import common.persistence.{PersistentLong, PersistentString}


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

  private def createRaftFile(filename: String): File = RAFT_DIR/filename/RAFT_STATE_EXTENSION
}


class RaftState private(initialTerm: Long = 0) {

  import RaftState._

  val currentTerm: PersistentLong = PersistentLong(createRaftFile("currentTerm"))
  val votedFor: PersistentString = PersistentString(createRaftFile("votedFor"))

  val commitIndex: Long = 0
  val lastApplied: Long = 0

  currentTerm.write(initialTerm)
}
