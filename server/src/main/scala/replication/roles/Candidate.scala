package replication.roles

import administration.Administration
import common.rpc.RequestTask
import common.time.{CancelTimer, ResetTimer}
import org.slf4j.{Logger, LoggerFactory}
import replication._
import replication.roles.RaftRole.MessageResult
import replication.state.{RaftIndividualTimeoutKey, RaftState}


private[replication] case object Candidate extends RaftRole {

  protected val log: Logger = LoggerFactory.getLogger(Candidate.getClass)

  /** Used for logging */
  override val roleName: String = "Candidate"


  /**
   * Handles a global (or at least global w.r.t. this server's Raft FSM) timeout event. Typically
   * the main source of role changes
   *
   * @param state current raft state
   * @return the timeout result
   */
override def processRaftGlobalTimeout(state: RaftState): Option[RaftRole] = Some(if (state.hasQuorum) Leader else Candidate)

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

    // For candidates, individual timeouts mean that a request vote reply was not received, so resend vote request
    val voteRequest = RequestVoteRequest(
      state.currentTerm.read().getOrElse(0),
      Administration.nodeID,
      state.log.lastLogIndex(),
      state.log.lastLogTerm()
    )

    MessageResult(Set(RequestTask(voteRequest, nodeID)), ResetTimer(RaftIndividualTimeoutKey(nodeID)), None)
  }

  /**
   * Handles a cluster reconfiguration event from the client, removing and/or adding nodes as required
   *
   * @param clusterEvent the information about what nodes are leaving or joining the cluster
   * @param state current raft state
   * @return the reconfiguration result
   */
  def processClusterReconfigEvent(clusterEvent: ClusterReconfigEvent)(state: RaftState)(implicit log: Logger): MessageResult = {
    ???
  }

  /**
   * Handle a direct append entry request received by this server. Only in the leader role is this
   * actually processed - otherwise it should be redirected to the current leader
   *
   * @param appendEvent the append entry event
   * @param state       current raft state
   * @return the event result
   */
  override def processAppendEntryEvent(appendEvent: AppendEntryEvent)(state: RaftState)(implicit log: Logger): MessageResult =
    Follower.processAppendEntryEvent(appendEvent)(state)

  /**
   * Handle an append entry request received from the leader
   *
   * @param appendRequest the append entry request from leader
   * @param state         current raft state
   * @return the event result
   */
  override def processAppendEntryRequest(appendRequest: AppendEntriesRequest)(state: RaftState)(implicit log: Logger): MessageResult =
    Follower.processAppendEntryRequest(appendRequest)(state)

  /**
   * Handle a response from an append entry request from followers. Determines whether an entry is
   * committed or not
   *
   * @param appendReply the append entry reply from followers
   * @param state       current raft state
   * @return the event result
   */
  override def processAppendEntryResult(appendReply: AppendEntriesResult)(state: RaftState)(implicit log: Logger): MessageResult =
    Follower.processAppendEntryResult(appendReply)(state)

  /**
   * Handle a vote request from a candidate, and decide whether or not to give that vote
   *
   * @param voteRequest the vote request from candidates
   * @param state current raft state
   * @return the event result
   */
  override def processRequestVoteRequest(voteRequest: RequestVoteRequest)(state: RaftState)(implicit log: Logger): MessageResult =
    Follower.processRequestVoteRequest(voteRequest)(state)

  /**
   * Handle a vote reply from a follower. Determines whether this server becomes the new leader
   *
   * @param voteReply the vote reply from followers
   * @param state     current raft state
   * @return the event result
   */
  override def processRequestVoteResult(voteReply: RequestVoteResult)(state: RaftState)(implicit log: Logger): MessageResult = {

    val nextRole = determineStepDown(voteReply.currentTerm)(state).orElse {

      // If we haven't stepped down as a result of the new message, and the vote was given, register reply and check
      // to see if we've won the election
      Option.when(voteReply.voteGiven && {state.registerReply(voteReply.followerId); state.hasQuorum})(Leader)
    }

    MessageResult(Set(), CancelTimer(RaftIndividualTimeoutKey(voteReply.followerId)), nextRole)
  }

}
