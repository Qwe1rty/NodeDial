package replication.roles

import common.rpc.{NoTask, ReplyTask}
import common.time.NothingTimer
import membership.MembershipActor
import membership.api.Membership
import org.slf4j.LoggerFactory
import replication._


private[replication] case object Follower extends RaftRole {

  private val log = LoggerFactory.getLogger(Follower.getClass)

  /** Used for logging */
  override val roleName: String = "Follower"

  /**
   * Handles a global (or at least global w.r.t. this server's Raft FSM) timeout event. Typically
   * the main source of role changes
   *
   * @param state current raft state
   * @return the timeout result
   */
  override def processRaftGlobalTimeout(state: RaftState): GlobalTimeoutResult = ???

  /**
   * Handles timeout for sending a request to a single node. For example, if this server is a leader,
   * a timeout for a specific node can occur if it hasn't been contacted in a while, necessitating
   * a heartbeat message to be sent out
   *
   * @param node  the node that timed out
   * @param state current raft state
   * @return the timeout result
   */
  override def processRaftIndividualTimeout(node: Membership, state: RaftState): IndividualTimeoutResult = ???

  /**
   * Handle a direct append entry request received by this server. Only in the leader role is this
   * actually processed - otherwise it should be redirected to the current leader
   *
   * @param appendEvent the append entry event
   * @param state       current raft state
   * @return the event result
   */
  override def processAppendEntryEvent(appendEvent: AppendEntryEvent)(node: Membership, state: RaftState): EventResult = ???

  /**
   * Handle an append entry request received from the leader
   *
   * @param appendRequest the append entry request from leader
   * @param state         current raft state
   * @return the event result
   */
  override def processAppendEntryRequest(appendRequest: AppendEntriesRequest)(node: Membership, state: RaftState): EventResult = ???

  /**
   * Handle a response from an append entry request from followers. Determines whether an entry is
   * committed or not
   *
   * @param appendReply the append entry reply from followers
   * @param state       current raft state
   * @return the event result
   */
  override def processAppendEntryResult(appendReply: AppendEntriesResult)(node: Membership, state: RaftState): EventResult = ???

  /**
   * Handle a vote request from a candidate, and decide whether or not to give that vote
   *
   * @param voteRequest the vote request from candidates
   * @param state       current raft state
   * @return the event result
   */
  override def processRequestVoteRequest(voteRequest: RequestVoteRequest)(node: Membership, state: RaftState): EventResult = {

    state.currentTerm.read().foreach(currentTerm => {

      if (voteRequest.candidateTerm < currentTerm) {
        return refuseVote(currentTerm)
      }

      if (!state.votedFor.read().contains(MembershipActor.nodeID)) {
        return refuseVote(currentTerm)
      }

      // TODO check candidate log recency

      return giveVote(currentTerm)
    })

    log.error("Current term was undefined! Invalid state")
    throw new IllegalStateException("Current Raft term value was undefined")
  }

  private def refuseVote(currentTerm: Long): EventResult =
    (ReplyTask(RequestVoteResult(currentTerm, voteGiven = false)), NothingTimer, Follower)

  private def giveVote(currentTerm: Long): EventResult =
    (ReplyTask(RequestVoteResult(currentTerm, voteGiven = true)), NothingTimer, Follower)

  /**
   * Handle a vote reply from a follower. Determines whether this server becomes the new leader
   *
   * @param voteReply the vote reply from followers
   * @param state     current raft state
   * @return the event result
   */
  override def processRequestVoteResult(voteReply: RequestVoteResult)(node: Membership, state: RaftState): EventResult =
    (NoTask, NothingTimer, Follower)
}
