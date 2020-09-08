package replication.roles

import administration.Administration
import common.rpc.{RPCTask, ReplyFutureTask, ReplyTask}
import common.time.ContinueTimer
import org.slf4j.{Logger, LoggerFactory}
import replication.ConfigEntry.ClusterChangeType
import replication._
import replication.roles.RaftRole.MessageResult
import replication.state.{RaftMessage, RaftState}


private[replication] case object Follower extends RaftRole {

  protected val log: Logger = LoggerFactory.getLogger(Follower.getClass)

  /** Used for logging */
  override val roleName: String = "Follower"


  /**
   * Handles a global (or at least global w.r.t. this server's Raft FSM) timeout event. Typically
   * the main source of role changes
   *
   * @param state current raft state
   * @return the timeout result
   */
  override def processRaftGlobalTimeout(state: RaftState): Option[RaftRole] = Some(Candidate)

  /**
   * Handles timeout for sending a request to a single node. For example, if this server is a leader,
   * a timeout for a specific node can occur if it hasn't been contacted in a while, necessitating
   * a heartbeat message to be sent out
   *
   * @param nodeID  the node that timed out
   * @param state current raft state
   * @return the timeout result
   */
  override def processRaftIndividualTimeout(nodeID: String)(state: RaftState)(implicit log: Logger): MessageResult = {

    // For followers, nothing needs to happen, and occur as holdovers from previous roles
    MessageResult(Set(), ContinueTimer, None)
  }

  /**
   * Handles a node adding event from the client
   *
   * @param addNodeEvent info about new node
   * @param state current raft state
   * @return the reconfiguration result
   */
  override def processAddNodeEvent(addNodeEvent: AddNodeEvent)(state: RaftState)(implicit log: Logger): MessageResult = {

    // Simply reject the add node event, since only the leader can actually execute the request
    MessageResult(Set(ReplyTask(AddNodeAck(status = false, state.currentLeader.map(_.nodeID)))), ContinueTimer, None)
  }

  /**
   * Handle a direct append entry request received by this server. Only in the leader role is this
   * actually processed - otherwise it should be redirected to the current leader
   *
   * @param appendEvent the append entry event
   * @param state       current raft state
   * @return the event result
   */
  override def processAppendEntryEvent(appendEvent: AppendEntryEvent)(state: RaftState)(implicit log: Logger): MessageResult = {

    val forwardTask: Set[RPCTask[RaftMessage]] = state.currentLeader
      .map(leader => ReplyFutureTask(appendEvent, leader.nodeID))
      .iterator
      .to(Set)

    MessageResult(forwardTask, ContinueTimer, None)
  }

  /**
   * Handle an append entry request received from the leader
   *
   * @param appendRequest the append entry request from leader
   * @param state current raft state
   * @return the event result
   */
  override def processAppendEntryRequest(appendRequest: AppendEntriesRequest)(state: RaftState)(implicit log: Logger): MessageResult = {

    log.info(s"Append entry request received from node ${appendRequest.leaderId} with term ${appendRequest.leaderTerm}")

    val currentTerm: Long = state.currentTerm.read().getOrElse(0)
    val nextRole = determineStepDown(appendRequest.leaderTerm)(state)

    // If leader's term is outdated, reject
    if (appendRequest.leaderTerm < currentTerm) rejectEntry(currentTerm, nextRole)

    // If the log entries don't match up, then reject entries, and it indicates logs prior to this entry are inconsistent
    else if (
      state.log.lastLogIndex() < appendRequest.prevLogIndex ||
        state.log.termOf(appendRequest.prevLogIndex) != appendRequest.prevLogTerm) {

      rejectEntry(currentTerm, nextRole)
    }

    // New log entry (or entries) can now be appended
    else {

      // If the new entry conflicts with existing follower log entries, then rollback follower log
      if (state.log.lastLogIndex() > appendRequest.prevLogIndex) state.log.rollback(appendRequest.prevLogIndex + 1)

      // Append entries and send success message, implying follower & leader logs are now fully in sync
      for (entry <- appendRequest.entries) {
        state.log.append(appendRequest.leaderTerm, entry.logEntry.toByteArray)

        // Special case for cluster configuration changes - must be applied at WAL stage, not commit stage
        entry.logEntry.entryType.cluster.foreach(configEntry => configEntry.changeType match {
          case ClusterChangeType.ADD =>
            log.info(s"Cluster node add received from leader for node ${configEntry.node.nodeId}")
            state.addNode(state.raftNodeToMembership(configEntry.node))

          case ClusterChangeType.REMOVE =>
            log.info(s"Cluster node remove received from leader for node ${configEntry.node.nodeId}")
            state.removeNode(configEntry.node.nodeId)

          case ClusterChangeType.Unrecognized(value) =>
            throw new IllegalArgumentException(s"Unknown cluster configuration change type: $value")
        })
      }
      if (appendRequest.leaderCommitIndex > state.commitIndex) {
        state.commitIndex = Math.min(state.log.lastLogIndex(), appendRequest.leaderCommitIndex)
      }

      acceptEntry(currentTerm, nextRole)
    }

  }

  /**
   * Handle a response from an append entry request from followers. Determines whether an entry is
   * committed or not
   *
   * @param appendReply the append entry reply from followers
   * @param state       current raft state
   * @return the event result
   */
  override def processAppendEntryResult(appendReply: AppendEntriesResult)(state: RaftState)(implicit log: Logger): MessageResult =
    stepDownIfBehind(appendReply.currentTerm, state)

  /**
   * Handle a vote request from a candidate, and decide whether or not to give that vote
   *
   * @param voteRequest the vote request from candidates
   * @param state current raft state
   * @return the event result
   */
  override def processRequestVoteRequest(voteRequest: RequestVoteRequest)(state: RaftState)(implicit log: Logger): MessageResult = {

    log.info(s"Vote request received from node ${voteRequest.candidateId} with term ${voteRequest.candidateTerm}")

    val currentTerm: Long = state.currentTerm.read().getOrElse(0)
    val nextRole = determineStepDown(voteRequest.candidateTerm)(state)

    // If candidate's term is outdated, or we voted for someone else already
    if (voteRequest.candidateTerm < currentTerm || !state.votedFor.read().contains(Administration.nodeID)) {
      refuseVote(currentTerm, nextRole)
    }

    // If this follower's log is more up-to-date, then refuse vote
    else if (
      voteRequest.lastLogTerm < state.log.lastLogTerm() ||
        voteRequest.lastLogTerm == state.log.lastLogTerm() && voteRequest.lastLogIndex < state.log.lastLogIndex()) {

      refuseVote(currentTerm, nextRole)
    }

    else giveVote(currentTerm, nextRole)
  }

  /**
   * Handle a vote reply from a follower. Determines whether this server becomes the new leader
   *
   * @param voteReply the vote reply from followers
   * @param state current raft state
   * @return the event result
   */
  override def processRequestVoteResult(voteReply: RequestVoteResult)(state: RaftState)(implicit log: Logger): MessageResult =
    stepDownIfBehind(voteReply.currentTerm, state)
}
