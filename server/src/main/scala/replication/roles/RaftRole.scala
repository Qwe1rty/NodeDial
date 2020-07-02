package replication.roles

import common.rpc.RPCTask
import common.time.TimerTask
import membership.api.Membership
import replication._


/**
 * RaftRole represents one of the Raft server states (Leader, Candidate, Follower)
 *
 * Since each of the 3 roles handles Raft events differently, they will need to implement
 */
trait RaftRole {

  /** The result types contains information about what actions need to be done as a result of an event:
   *   - any network actions that need to be taken
   *   - any timer actions
   *   - the next role state
   */
  type EventResult = (RPCTask[RaftMessage], TimerTask[RaftGlobalTimeoutKey.type], RaftRole)
  type GlobalTimeoutResult = (RPCTask[RaftMessage], RaftRole)
  type IndividualTimeoutResult = (RPCTask[RaftMessage], TimerTask[RaftIndividualTimeoutKey], RaftRole)

  /** Used for logging */
  val roleName: String


  /** Ingest a Raft event and return the event result */
  final def processRaftEvent(event: RaftEvent, state: RaftState): EventResult = {
    (event.message match {
      case appendEvent:   AppendEntryEvent     => processAppendEntryEvent(appendEvent) _
      case appendRequest: AppendEntriesRequest => processAppendEntryRequest(appendRequest) _
      case appendReply:   AppendEntriesResult  => processAppendEntryResult(appendReply) _
      case voteRequest:   RequestVoteRequest   => processRequestVoteRequest(voteRequest) _
      case voteReply:     RequestVoteResult    => processRequestVoteResult(voteReply) _
    })(event.node, state)
  }

  /**
   * Handles a global (or at least global w.r.t. this server's Raft FSM) timeout event. Typically
   * the main source of role changes
   *
   * @param state current raft state
   * @return the timeout result
   */
  def processRaftGlobalTimeout(state: RaftState): GlobalTimeoutResult

  /**
   * Handles timeout for sending a request to a single node. For example, if this server is a leader,
   * a timeout for a specific node can occur if it hasn't been contacted in a while, necessitating
   * a heartbeat message to be sent out
   *
   * @param node the node that timed out
   * @param state current raft state
   * @return the timeout result
   */
  def processRaftIndividualTimeout(node: Membership, state: RaftState): IndividualTimeoutResult

  /**
   * Handle a direct append entry request received by this server. Only in the leader role is this
   * actually processed - otherwise it should be redirected to the current leader
   *
   * @param appendEvent the append entry event
   * @param state current raft state
   * @return the event result
   */
  def processAppendEntryEvent(appendEvent: AppendEntryEvent)(node: Membership, state: RaftState): EventResult

  /**
   * Handle an append entry request received from the leader
   *
   * @param appendRequest the append entry request from leader
   * @param state current raft state
   * @return the event result
   */
  def processAppendEntryRequest(appendRequest: AppendEntriesRequest)(node: Membership, state: RaftState): EventResult

  /**
   * Handle a response from an append entry request from followers. Determines whether an entry is
   * committed or not
   *
   * @param appendReply the append entry reply from followers
   * @param state current raft state
   * @return the event result
   */
  def processAppendEntryResult(appendReply: AppendEntriesResult)(node: Membership, state: RaftState): EventResult

  /**
   * Handle a vote request from a candidate, and decide whether or not to give that vote
   *
   * @param voteRequest the vote request from candidates
   * @param state current raft state
   * @return the event result
   */
  def processRequestVoteRequest(voteRequest: RequestVoteRequest)(node: Membership, state: RaftState): EventResult

  /**
   * Handle a vote reply from a follower. Determines whether this server becomes the new leader
   *
   * @param voteReply the vote reply from followers
   * @param state current raft state
   * @return the event result
   */
  def processRequestVoteResult(voteReply: RequestVoteResult)(node: Membership, state: RaftState): EventResult
}
