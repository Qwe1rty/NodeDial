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
    nextRole:   Option[RaftRole]
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


  // Raft event handler methods (with default implementations)

  /**
   * Handle an append entry request received from the leader
   *
   * @param appendRequest the append entry request from leader
   * @param state current raft state
   * @return the event result
   */
  def processAppendEntryRequest(appendRequest: AppendEntriesRequest)(node: Membership, state: RaftState): MessageResult = {

    log.info(s"Append entry request received from node ${node.nodeID} with IP address ${node.ipAddress}")

    state.currentTerm.read().foreach(currentTerm => {
      val nextRole = determineStepDown(appendRequest.leaderTerm)(state)

      // If leader's term is outdated, or the last log entry doesn't match
      if (appendRequest.leaderTerm < currentTerm) {
        rejectEntry(currentTerm, nextRole)
      }

      // If the log entries don't match up, then reject entries, and it indicates logs prior to this entry are inconsistent
      else if (
        state.replicatedLog.lastLogIndex() < appendRequest.prevLogIndex ||
        state.replicatedLog.termOf(appendRequest.prevLogIndex) != appendRequest.prevLogTerm) {

        rejectEntry(currentTerm, nextRole)
      }

      // New log entry (or entries) can now be appended
      else {
        
        // If the new entry conflicts with existing follower log entries, then rollback follower log
        if (state.replicatedLog.lastLogIndex() > appendRequest.prevLogIndex) {
          state.replicatedLog.rollback(appendRequest.prevLogIndex + 1)
        } 

        // Append entries and send success message, implying follower & leader logs are now fully in sync
        for (entry <- appendRequest.entries) {
          state.replicatedLog.append(appendRequest.leaderTerm, entry.logEntry.toByteArray)
        }
        if (appendRequest.leaderCommitIndex > state.commitIndex) {
          state.commitIndex = Math.min(state.replicatedLog.lastLogIndex(), appendRequest.leaderCommitIndex)
        }

        // TODO start commit task?

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
   * @param state current raft state
   * @return the event result
   */
  def processAppendEntryResult(appendReply: AppendEntriesResult)(node: Membership, state: RaftState): MessageResult =
    stepDownIfBehind(appendReply.currentTerm, state)

  /**
   * Handle a vote request from a candidate, and decide whether or not to give that vote
   *
   * @param voteRequest the vote request from candidates
   * @param state current raft state
   * @return the event result
   */
  def processRequestVoteRequest(voteRequest: RequestVoteRequest)(node: Membership, state: RaftState): MessageResult = {

    log.info(s"Vote request received from node ${node.nodeID} with IP address ${node.ipAddress}")

    state.currentTerm.read().foreach(currentTerm => {
      val nextRole = determineStepDown(voteRequest.candidateTerm)(state)

      // If candidate's term is outdated, or we voted for someone else already
      if (voteRequest.candidateTerm < currentTerm || !state.votedFor.read().contains(MembershipActor.nodeID)) {
        refuseVote(currentTerm, nextRole)
      }

      // If this follower's log is more up-to-date, then refuse vote
      else if (
        voteRequest.lastLogTerm < state.replicatedLog.lastLogTerm() ||
        voteRequest.lastLogTerm == state.replicatedLog.lastLogTerm() && voteRequest.lastLogIndex < state.replicatedLog.lastLogIndex()) {

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
  def processRequestVoteResult(voteReply: RequestVoteResult)(node: Membership, state: RaftState): MessageResult =
    stepDownIfBehind(voteReply.currentTerm, state)


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

  final protected def stepDownIfBehind(messageTerm: Long, state: RaftState): MessageResult =
    MessageResult(NoTask, ContinueTimer, determineStepDown(messageTerm)(state))

  final protected def rejectEntry(currentTerm: Long, nextRole: Option[RaftRole]): MessageResult =
    MessageResult(ReplyTask(AppendEntriesResult(currentTerm, success = false)), ContinueTimer, nextRole)

  final protected def acceptEntry(currentTerm: Long, nextRole: Option[RaftRole]): MessageResult =
    MessageResult(ReplyTask(AppendEntriesResult(currentTerm, success = true)), ResetTimer(RaftGlobalTimeoutKey), nextRole)

  final protected def refuseVote(currentTerm: Long, nextRole: Option[RaftRole]): MessageResult =
    MessageResult(ReplyTask(RequestVoteResult(currentTerm, voteGiven = false)), ContinueTimer, nextRole)

  final protected def giveVote(currentTerm: Long, nextRole: Option[RaftRole]): MessageResult =
    MessageResult(ReplyTask(RequestVoteResult(currentTerm, voteGiven = true)), ResetTimer(RaftGlobalTimeoutKey), nextRole)
}
