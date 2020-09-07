package replication.state

import administration.Membership
import common.persistence.{PersistentLong, PersistentString}
import replication.eventlog.ReplicatedLog
import replication.{AppendEntryEvent, Raft}

import scala.collection.immutable.Queue


/**
 * RaftState are the state variables that are used by the raft algorithm to track things
 * like election status, log entries, etc.
 *
 * Since some variables need to be persisted to disk, the class is inherently not immutable
 * and therefore the RaftState class is defined as a mutable object
 */
class RaftState(val selfInfo: Membership, val log: ReplicatedLog) extends RaftCluster(selfInfo) {

  import RaftState._

  // Common state variables, for all roles
  val currentTerm: PersistentLong = PersistentLong(Raft.RAFT_DIR/("currentTerm" + RAFT_STATE_EXTENSION))
  val votedFor: PersistentString = PersistentString(Raft.RAFT_DIR/("votedFor" + RAFT_STATE_EXTENSION))

  var currentLeader: Option[Membership] = None
  var bufferedAppendEvents: Queue[AppendEntryEvent] = Queue[AppendEntryEvent]() // TODO implement this eventually

  var commitIndex: Int = 0
  var lastApplied: Int = 0

  var commitInProgress: Boolean = false

  // Leader-only state variables
  var leaderState: RaftLeaderState = RaftLeaderState(cluster(), log.size())
  var pendingMember: Option[Membership] = None
  var pendingConfigIndex: Option[Int] = None


  if (!currentTerm.exists()) currentTerm.write(0)
}

object RaftState {

  val RAFT_STATE_EXTENSION = ".state"

  def apply(selfInfo: Membership, replicatedLog: ReplicatedLog): RaftState =
    new RaftState(selfInfo, replicatedLog)
}
