package replication.roles

import replication._


case object Leader extends RaftRole {

  /** Used for logging */
  override val roleName: String = "Leader"

  /**
   * Handle a direct append entry request received by this server. Only in the leader role is this
   * actually processed - otherwise it should be redirected to the current leader
   *
   * @param appendEvent the append entry event
   * @param state       current raft state
   * @return the event result
   */
  override def processAppendEntryEvent(appendEvent: AppendEntryEvent)(state: RaftState): (Option[RPCTask[RaftMessage]], RaftRole) = ???

  /**
   * Handle an append entry request received from the leader
   *
   * @param appendRequest the append entry request from leader
   * @param state         current raft state
   * @return the event result
   */
  override def processAppendEntryRequest(appendRequest: AppendEntriesRequest)(state: RaftState): (Option[RPCTask[RaftMessage]], RaftRole) = ???

  /**
   * Handle a response from an append entry request from followers. Determines whether an entry is
   * committed or not
   *
   * @param appendReply the append entry reply from followers
   * @param state       current raft state
   * @return the event result
   */
  override def processAppendEntryResult(appendReply: AppendEntriesResult)(state: RaftState): (Option[RPCTask[RaftMessage]], RaftRole) = ???

  /**
   * Handle a vote request from a candidate, and decide whether or not to give that vote
   *
   * @param voteRequest the vote request from candidates
   * @param state       current raft state
   * @return the event result
   */
  override def processRequestVoteRequest(voteRequest: RequestVoteRequest)(state: RaftState): (Option[RPCTask[RaftMessage]], RaftRole) = ???

  /**
   * Handle a vote reply from a follower. Determines whether this server becomes the new leader
   *
   * @param voteReply the vote reply from followers
   * @param state     current raft state
   * @return the event result
   */
  override def processRequestVoteResult(voteReply: RequestVoteResult)(state: RaftState): (Option[RPCTask[RaftMessage]], RaftRole) = ???
}
