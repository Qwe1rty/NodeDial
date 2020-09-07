package replication.roles

import administration.{Administration, Membership}
import common.rpc.{NoTask, RPCTask, ReplyFutureTask, ReplyTask}
import common.time.{ContinueTimer, ResetTimer, TimerTask}
import org.slf4j.Logger
import replication._
import replication.roles.RaftRole.MessageResult
import replication.state._


private[replication] object RaftRole {

  /** The MessageResult types contains information about what actions need to be done as a result of an event:
   *   - any network actions that need to be taken, which may reset timers (such as individual heartbeat timers)
   *   - any timer actions
   *   - the next role state. A Some(...) value triggers role transition functions, None does not
   */
  case class MessageResult(
    rpcTask:   Set[RPCTask[RaftMessage]],
    timerTask: TimerTask[RaftTimeoutKey],
    nextRole:  Option[RaftRole]
  )
}


/**
 * RaftRole represents one of the Raft server states (Leader, Candidate, Follower)
 *
 * Since each of the 3 roles handles Raft events differently, they will need to implement
 */
private[replication] trait RaftRole {

  /** Used for logging */
  val roleName: String
  implicit protected val log: Logger


  /** Ingest a Raft message event and return the event result */
  final def processRaftEvent(message: RaftMessage, state: RaftState): MessageResult = {
    (message match {
      case reconfigEvent: ClusterReconfigEvent => processClusterReconfigEvent(reconfigEvent) _
      case appendEvent:   AppendEntryEvent     => processAppendEntryEvent(appendEvent) _
      case appendRequest: AppendEntriesRequest => processAppendEntryRequest(appendRequest) _
      case appendReply:   AppendEntriesResult  => processAppendEntryResult(appendReply) _
      case voteRequest:   RequestVoteRequest   => processRequestVoteRequest(voteRequest) _
      case voteReply:     RequestVoteResult    => processRequestVoteResult(voteReply) _
    })(state)
  }

  /** Ingest a Raft timeout event and return the timeout result */
  final def processRaftTimeout(raftTimeoutTick: RaftTimeoutTick, state: RaftState): MessageResult = {
    raftTimeoutTick match {
      case RaftIndividualTimeoutTick(node) => processRaftIndividualTimeout(node)(state)
      case RaftGlobalTimeoutTick           => MessageResult(Set(NoTask), ContinueTimer, processRaftGlobalTimeout(state))
    }
  }


  // Raft event handler methods (without implementations) to override in concrete classes below:

  /**
   * Handles a global (or at least global w.r.t. this server's Raft FSM) timeout event. Typically
   * the main source of role changes
   *
   * @param state current raft state
   * @return the timeout result
   */
  def processRaftGlobalTimeout(state: RaftState): Option[RaftRole]

  /**
   * Handles timeout for sending a request to a single node. For example, if this server is a leader,
   * a timeout for a specific node can occur if it hasn't been contacted in a while, necessitating
   * a heartbeat message to be sent out
   *
   * @param nodeID the node that timed out
   * @param state current raft state
   * @return the timeout result
   */
  def processRaftIndividualTimeout(nodeID: String)(state: RaftState)(implicit log: Logger): MessageResult

  /**
   * Handles a cluster reconfiguration event from the client, removing and/or adding nodes as required
   *
   * @param clusterEvent the information about what nodes are leaving or joining the cluster
   * @param state current raft state
   * @return the reconfiguration result
   */
  def processClusterReconfigEvent(clusterEvent: ClusterReconfigEvent)(state: RaftState)(implicit log: Logger): MessageResult

  /**
   * Handle a direct append entry request received by this server. Only in the leader role is this
   * actually processed - otherwise it should be redirected to the current leader if a leader exists
   *
   * @param appendEvent the append entry event
   * @param state current raft state
   * @return the event result
   */
  def processAppendEntryEvent(appendEvent: AppendEntryEvent)(state: RaftState)(implicit log: Logger): MessageResult


  // Raft event handler methods (with default implementations)

  /**
   * Handle an append entry request received from the leader
   *
   * @param appendRequest the append entry request from leader
   * @param state current raft state
   * @return the event result
   */
  def processAppendEntryRequest(appendRequest: AppendEntriesRequest)(state: RaftState)(implicit log: Logger): MessageResult

  /**
   * Handle a response from an append entry request from followers. Determines whether an entry is
   * committed or not
   *
   * @param appendReply the append entry reply from followers
   * @param state current raft state
   * @return the event result
   */
  def processAppendEntryResult(appendReply: AppendEntriesResult)(state: RaftState)(implicit log: Logger): MessageResult

  /**
   * Handle a vote request from a candidate, and decide whether or not to give that vote
   *
   * @param voteRequest the vote request from candidates
   * @param state current raft state
   * @return the event result
   */
  def processRequestVoteRequest(voteRequest: RequestVoteRequest)(state: RaftState)(implicit log: Logger): MessageResult

  /**
   * Handle a vote reply from a follower. Determines whether this server becomes the new leader
   *
   * @param voteReply the vote reply from followers
   * @param state current raft state
   * @return the event result
   */
  def processRequestVoteResult(voteReply: RequestVoteResult)(state: RaftState)(implicit log: Logger): MessageResult


  // Implementation details:

  /**
   * Compare an incoming message's term to this server's term, and determine if we need to step down
   * to a follower role. This is required for all Raft message types, including requests and replies
   *
   * @param receivedTerm term of received message
   * @param state current state
   * @return new Raft role
   */
  final protected def determineStepDown(receivedTerm: Long)(state: RaftState): Option[RaftRole] = {
    Option.when(receivedTerm > state.currentTerm.read().get) {
      state.currentTerm.write(receivedTerm)
      Follower
    }
  }

  final protected def stepDownIfBehind(messageTerm: Long, state: RaftState): MessageResult =
    MessageResult(
      Set(),
      ContinueTimer,
      determineStepDown(messageTerm)(state)
    )

  final protected def rejectEntry(currentTerm: Long, nextRole: Option[RaftRole]): MessageResult =
    MessageResult(
      Set(ReplyTask(AppendEntriesResult(currentTerm, Administration.nodeID, success = false))),
      ContinueTimer,
      nextRole
    )

  final protected def acceptEntry(currentTerm: Long, nextRole: Option[RaftRole]): MessageResult =
    MessageResult(
      Set(ReplyTask(AppendEntriesResult(currentTerm, Administration.nodeID, success = true))),
      ResetTimer(RaftGlobalTimeoutKey),
      nextRole
    )

  final protected def refuseVote(currentTerm: Long, nextRole: Option[RaftRole]): MessageResult =
    MessageResult(
      Set(ReplyTask(RequestVoteResult(currentTerm, Administration.nodeID, voteGiven = false))),
      ContinueTimer,
      nextRole
    )

  final protected def giveVote(currentTerm: Long, nextRole: Option[RaftRole]): MessageResult =
    MessageResult(
      Set(ReplyTask(RequestVoteResult(currentTerm, Administration.nodeID, voteGiven = true))),
      ResetTimer(RaftGlobalTimeoutKey),
      nextRole
    )
}
