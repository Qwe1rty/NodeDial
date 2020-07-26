package replication.roles

import common.rpc.{NoTask, RequestTask}
import common.time.{ContinueTimer, ResetTimer}
import membership.MembershipActor
import membership.api.Membership
import org.slf4j.{Logger, LoggerFactory}
import replication._
import replication.roles.RaftRole.MessageResult
import replication.state.RaftLeaderState.LogIndexState
import replication.state.{RaftIndividualTimeoutKey, RaftState}


private[replication] case object Leader extends RaftRole {

  protected val log: Logger = LoggerFactory.getLogger(Leader.getClass)

  /** Used for logging */
  override val roleName: String = "Leader"


  /**
   * Handles a global (or at least global w.r.t. this server's Raft FSM) timeout event. Typically
   * the main source of role changes
   *
   * @param state current raft state
   * @return the timeout result
   */
  override def processRaftGlobalTimeout(state: RaftState): Option[RaftRole] = None

  /**
   * Handles timeout for sending a request to a single node. For example, if this server is a leader,
   * a timeout for a specific node can occur if it hasn't been contacted in a while, necessitating
   * a heartbeat message to be sent out
   *
   * @param node  the node that timed out
   * @param state current raft state
   * @return the timeout result
   */
  override def processRaftIndividualTimeout(node: Membership, state: RaftState): MessageResult = {

    // For leaders, individual timeouts mean a node has not received a heartbeat/request in a while
    state.currentTerm.read().foreach { currentTerm =>

      val followerPrevIndex = state.leaderState(node.nodeID).nextIndex - 1
      val appendEntriesRequest = AppendEntriesRequest(
        currentTerm,
        MembershipActor.nodeID,
        followerPrevIndex,
        state.replicatedLog.termOf(followerPrevIndex),
        Seq.empty,
        state.commitIndex
      )

      MessageResult(RequestTask(appendEntriesRequest, node), ContinueTimer, None)
    }

    log.error("Current term was undefined! Invalid state")
    throw new IllegalStateException("Current Raft term value was undefined")
  }

  /**
   * Handle a direct append entry request received by this server. Only in the leader role is this
   * actually processed - otherwise it should be redirected to the current leader
   *
   * @param appendEvent the append entry event
   * @param state       current raft state
   * @return the event result
   */
  override def processAppendEntryEvent(appendEvent: AppendEntryEvent)(node: Membership, state: RaftState): MessageResult = ???

  /**
   * Handle an append entry request received from the leader
   *
   * @param appendRequest the append entry request from leader
   * @param state         current raft state
   * @return the event result
   */
  override def processAppendEntryRequest(appendRequest: AppendEntriesRequest)(node: Membership, state: RaftState): MessageResult = ???

  /**
   * Handle a response from an append entry request from followers. Determines whether an entry is
   * committed or not
   *
   * @param appendReply the append entry reply from followers
   * @param state       current raft state
   * @return the event result
   */
  override def processAppendEntryResult(appendReply: AppendEntriesResult)(node: Membership, state: RaftState): MessageResult = {

    state.currentTerm.read().foreach { currentTerm =>
      val nextRole = determineStepDown(appendReply.currentTerm)(state)

      // Due to things like network partitions, a new leader of higher term may exist. We step down in this case
      if (nextRole.contains(Follower)) {
        return MessageResult(NoTask, ContinueTimer, nextRole)
      }

      // If successful, we're guaranteed that the follower log is consistent with the leader log, and we need to update
      // the known up-to-dateness
      if (appendReply.success) {
        state.leaderState = state.leaderState.patch(node.nodeID, currentIndexState => LogIndexState(
          currentIndexState.nextIndex + 1,
          currentIndexState.nextIndex
        ))

        MessageResult(NoTask, ResetTimer(RaftIndividualTimeoutKey(node)), None)
      }

      // Otherwise, follower log is inconsistent with leader log, so we roll back one entry and retry
      else {
        state.leaderState.patchNextIndex(node.nodeID, _ - 1)

        val followerPrevIndex = state.leaderState(node.nodeID).nextIndex - 1
        val appendEntriesRequest = AppendEntriesRequest(
          currentTerm,
          MembershipActor.nodeID,
          followerPrevIndex,
          state.replicatedLog.termOf(followerPrevIndex),
          Seq.empty,
          state.commitIndex
        )

        MessageResult(RequestTask(appendEntriesRequest, node), ContinueTimer, None)
      }
    }

    log.error("Current term was undefined! Invalid state")
    throw new IllegalStateException("Current Raft term value was undefined")
  }

  /**
   * Handle a vote request from a candidate, and decide whether or not to give that vote
   *
   * @param voteRequest the vote request from candidates
   * @param state current raft state
   * @return the event result
   */
  override def processRequestVoteRequest(voteRequest: RequestVoteRequest)(node: Membership, state: RaftState): MessageResult =
    super.processRequestVoteRequest(voteRequest)(node, state)

  /**
   * Handle a vote reply from a follower. Determines whether this server becomes the new leader
   *
   * @param voteReply the vote reply from followers
   * @param state current raft state
   * @return the event result
   */
  override def processRequestVoteResult(voteReply: RequestVoteResult)(node: Membership, state: RaftState): MessageResult =
    super.processRequestVoteResult(voteReply)(node, state)
}
