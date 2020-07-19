package replication.roles

import common.rpc.RequestTask
import common.time.ResetTimer
import membership.MembershipActor
import membership.api.Membership
import org.slf4j.{Logger, LoggerFactory}
import replication._
import replication.roles.RaftRole.MessageResult


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

      val appendEntriesRequest = AppendEntriesRequest(
        currentTerm,
        MembershipActor.nodeID,
        state.replicatedLog.lastLogIndex(),
        state.replicatedLog.lastLogTerm(),
        Seq.empty,
        state.commitIndex
      )

      MessageResult(
        RequestTask(appendEntriesRequest, node.ipAddress),
        ResetTimer(RaftIndividualTimeoutKey(node)),
        None
      )
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
  override def processAppendEntryResult(appendReply: AppendEntriesResult)(node: Membership, state: RaftState): MessageResult = ???

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
