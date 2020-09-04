package replication.roles

import administration.{Administration, Membership}
import common.rpc.{RPCTask, ReplyFutureTask}
import common.time.ContinueTimer
import org.slf4j.{Logger, LoggerFactory}
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
   * @param node  the node that timed out
   * @param state current raft state
   * @return the timeout result
   */
  override def processRaftIndividualTimeout(node: Membership, state: RaftState)(implicit log: Logger): MessageResult = {

    // For followers, nothing needs to happen, and occur as holdovers from previous roles
    MessageResult(Set(), ContinueTimer, None)
  }

  /**
   * Handle a direct append entry request received by this server. Only in the leader role is this
   * actually processed - otherwise it should be redirected to the current leader
   *
   * @param appendEvent the append entry event
   * @param state       current raft state
   * @return the event result
   */
  override def processAppendEntryEvent(appendEvent: AppendEntryEvent)(node: Membership, state: RaftState)(implicit log: Logger): MessageResult = {

    val forwardTask: Set[RPCTask[RaftMessage]] = state.currentLeader
      .map(ReplyFutureTask(appendEvent, _))
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
  override def processAppendEntryRequest(appendRequest: AppendEntriesRequest)(node: Membership, state: RaftState)(implicit log: Logger): MessageResult = {

    log.info(s"Append entry request received from node ${node.nodeID} with IP address ${node.ipAddress}")

    state.currentTerm.read().foreach(currentTerm => {
      val nextRole = determineStepDown(appendRequest.leaderTerm)(state)

      // If leader's term is outdated, or the last log entry doesn't match
      if (appendRequest.leaderTerm < currentTerm) {
        rejectEntry(currentTerm, nextRole)
      }

      // If the log entries don't match up, then reject entries, and it indicates logs prior to this entry are inconsistent
      else if (
        state.log.lastLogIndex() < appendRequest.prevLogIndex ||
          state.log.termOf(appendRequest.prevLogIndex) != appendRequest.prevLogTerm) {

        rejectEntry(currentTerm, nextRole)
      }

      // New log entry (or entries) can now be appended
      else {

        // If the new entry conflicts with existing follower log entries, then rollback follower log
        if (state.log.lastLogIndex() > appendRequest.prevLogIndex) {
          state.log.rollback(appendRequest.prevLogIndex + 1)
        }

        // Append entries and send success message, implying follower & leader logs are now fully in sync
        for (entry <- appendRequest.entries) {
          state.log.append(appendRequest.leaderTerm, entry.logEntry.toByteArray)
        }
        if (appendRequest.leaderCommitIndex > state.commitIndex) {
          state.commitIndex = Math.min(state.log.lastLogIndex(), appendRequest.leaderCommitIndex)
        }

        acceptEntry(currentTerm, nextRole)
      }
    })

    log.error("Current term was undefined! Invalid state")
    throw new IllegalStateException("Current Raft term value was undefined")
  }

  /**
   * Handle a response from an append entry request from followers. Determines whether an entry is
   * committed or not
   *
   * @param appendReply the append entry reply from followers
   * @param state       current raft state
   * @return the event result
   */
  override def processAppendEntryResult(appendReply: AppendEntriesResult)(node: Membership, state: RaftState)(implicit log: Logger): MessageResult =
    stepDownIfBehind(appendReply.currentTerm, state)

  /**
   * Handle a vote request from a candidate, and decide whether or not to give that vote
   *
   * @param voteRequest the vote request from candidates
   * @param state current raft state
   * @return the event result
   */
  override def processRequestVoteRequest(voteRequest: RequestVoteRequest)(node: Membership, state: RaftState)(implicit log: Logger): MessageResult = {

    log.info(s"Vote request received from node ${node.nodeID} with IP address ${node.ipAddress}")

    state.currentTerm.read().foreach(currentTerm => {
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
    })

    log.error("Current term was undefined! Invalid state")
    throw new IllegalStateException("Current Raft term value was undefined")
  }

  /**
   * Handle a vote reply from a follower. Determines whether this server becomes the new leader
   *
   * @param voteReply the vote reply from followers
   * @param state current raft state
   * @return the event result
   */
  override def processRequestVoteResult(voteReply: RequestVoteResult)(node: Membership, state: RaftState)(implicit log: Logger): MessageResult =
    stepDownIfBehind(voteReply.currentTerm, state)
}
