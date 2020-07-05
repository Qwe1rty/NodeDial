package replication.roles

import common.rpc.{NoTask, RPCTask, ReplyTask}
import common.time.{ContinueTimer, ResetTimer, TimerTask}
import membership.MembershipActor
import membership.api.Membership
import org.slf4j.Logger
import replication.roles.RaftRole.MessageResult
import replication.{RaftMessage, _}


private[replication] object RaftRole {

  /** The MessageResult types contains information about what actions need to be done as a result of an event:
   *   - any network actions that need to be taken, which may reset timers (such as individual heartbeat timers)
   *   - any timer actions
   *   - the next role state. A Some(...) value triggers role transition functions, None does not
   */
  case class MessageResult(
    rpcTask:   RPCTask[RaftMessage],
    timerTask: TimerTask[RaftTimeoutKey],
    newRole:   Option[RaftRole]
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
  protected val log: Logger


  /** Ingest a Raft message event and return the event result */
  final def processRaftEvent(event: RaftEvent, state: RaftState): MessageResult = {
    (event.message match {
      case appendEvent:   AppendEntryEvent     => processAppendEntryEvent(appendEvent) _
      case appendRequest: AppendEntriesRequest => processAppendEntryRequest(appendRequest) _
      case appendReply:   AppendEntriesResult  => processAppendEntryResult(appendReply) _
      case voteRequest:   RequestVoteRequest   => processRequestVoteRequest(voteRequest) _
      case voteReply:     RequestVoteResult    => processRequestVoteResult(voteReply) _
    })(event.node, state)
  }

  /** Ingest a Raft timeout event and return the timeout result */
  final def processRaftTimeout(raftTimeoutTick: RaftTimeoutTick, state: RaftState): MessageResult = {
    raftTimeoutTick match {
      case RaftIndividualTimeoutTick(node) => processRaftIndividualTimeout(node, state)
      case RaftGlobalTimeoutTick           => MessageResult(NoTask, ContinueTimer, processRaftGlobalTimeout(state))
    }
  }


  // Methods to (potentially) override in concrete classes below:

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
   * @param node the node that timed out
   * @param state current raft state
   * @return the timeout result
   */
  def processRaftIndividualTimeout(node: Membership, state: RaftState): MessageResult

  /**
   * Handle a direct append entry request received by this server. Only in the leader role is this
   * actually processed - otherwise it should be redirected to the current leader
   *
   * @param appendEvent the append entry event
   * @param state current raft state
   * @return the event result
   */
  def processAppendEntryEvent(appendEvent: AppendEntryEvent)(node: Membership, state: RaftState): MessageResult

  /**
   * Handle an append entry request received from the leader
   *
   * @param appendRequest the append entry request from leader
   * @param state current raft state
   * @return the event result
   */
  def processAppendEntryRequest(appendRequest: AppendEntriesRequest)(node: Membership, state: RaftState): MessageResult

  /**
   * Handle a response from an append entry request from followers. Determines whether an entry is
   * committed or not
   *
   * @param appendReply the append entry reply from followers
   * @param state current raft state
   * @return the event result
   */
  def processAppendEntryResult(appendReply: AppendEntriesResult)(node: Membership, state: RaftState): MessageResult

  /**
   * Handle a vote request from a candidate, and decide whether or not to give that vote
   *
   * @param voteRequest the vote request from candidates
   * @param state current raft state
   * @return the event result
   */
  def processRequestVoteRequest(voteRequest: RequestVoteRequest)(node: Membership, state: RaftState): MessageResult = {

    state.currentTerm.read().foreach(currentTerm => {
      val newRole = determineStepDown(voteRequest.candidateTerm)(state)

      // If candidate's term is outdated, or we voted for someone else already
      if (voteRequest.candidateTerm < currentTerm || !state.votedFor.read().contains(MembershipActor.nodeID)) {
        return refuseVote(currentTerm, newRole)
      }

      // TODO check candidate log recency
      if (???) {
        return refuseVote(currentTerm, newRole)
      }

      return giveVote(currentTerm, newRole)
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
  def processRequestVoteResult(voteReply: RequestVoteResult)(node: Membership, state: RaftState): MessageResult =
    MessageResult(NoTask, ContinueTimer, determineStepDown(voteReply.currentTerm)(state))


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
    if (receivedTerm > state.currentTerm.read().get) {
      state.currentTerm.write(receivedTerm)
      Some(Follower)
    }
    else None
  }

  final protected def refuseVote(currentTerm: Long, newRole: Option[RaftRole]): MessageResult =
    MessageResult(ReplyTask(RequestVoteResult(currentTerm, voteGiven = false)), ContinueTimer, newRole)

  final protected def giveVote(currentTerm: Long, newRole: Option[RaftRole]): MessageResult =
    MessageResult(ReplyTask(RequestVoteResult(currentTerm, voteGiven = true)), ResetTimer(RaftGlobalTimeoutKey), newRole)
}
