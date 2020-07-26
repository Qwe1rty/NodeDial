package replication.state

import better.files.File
import common.ServerConstants
import common.persistence.{PersistentLong, PersistentString}
import membership.api.Membership
import replication.cluster.RaftCluster
import replication.eventlog.ReplicatedLog


/**
 * RaftState are the state variables that are used by the raft algorithm to track things
 * like election status, log entries, etc.
 *
 * Since some variables need to be persisted to disk, the class is inherently not immutable
 * and therefore the RaftState class is defined as a mutable object
 */
object RaftState {

  val RAFT_DIR: File       = ServerConstants.BASE_DIRECTORY/"raft"
  val RAFT_STATE_EXTENSION = ".state"


  def apply(selfInfo: Membership, replicatedLog: ReplicatedLog): RaftState =
    new RaftState(selfInfo, replicatedLog)
}


class RaftState(val selfInfo: Membership, val replicatedLog: ReplicatedLog)
  extends RaftCluster(selfInfo) {

  import RaftState._

  // Common state variables, for all roles
  val currentTerm: PersistentLong = PersistentLong(RAFT_DIR/"currentTerm"/RAFT_STATE_EXTENSION)
  val votedFor: PersistentString = PersistentString(RAFT_DIR/"votedFor"/RAFT_STATE_EXTENSION)

  var commitIndex: Long = 0
  var lastApplied: Long = 0

  // Leader-only state variables
  var leaderState: RaftLeaderState = RaftLeaderState(cluster(), replicatedLog.size())


  if (!currentTerm.exists()) currentTerm.write(0)
}
