package replication.roles

import common.persistence.ProtobufSerializer
import common.rpc.{BroadcastTask, NoTask, RPCTask, ReplyTask, RequestTask}
import common.time.{ContinueTimer, ResetTimer}
import membership.MembershipActor
import membership.api.Membership
import org.slf4j.{Logger, LoggerFactory}
import replication._
import replication.roles.Leader.serialize
import replication.roles.RaftRole.MessageResult
import replication.state.RaftLeaderState.LogIndexState
import replication.state.{RaftIndividualTimeoutKey, RaftMessage, RaftState}
import scalapb.GeneratedMessageCompanion

import scala.util.{Failure, Success, Try}


private[replication] case object Leader extends RaftRole with ProtobufSerializer[LogEntry] {

  protected val log: Logger = LoggerFactory.getLogger(Leader.getClass)

  override val messageCompanion: GeneratedMessageCompanion[LogEntry] = LogEntry

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
    MessageResult(Set(RequestTask(createAppendEntriesRequest(node.nodeID, state), node)), ContinueTimer, None)
  }

  /**
   * Handle a direct append entry request received by this server. Only in the leader role is this
   * actually processed - otherwise it should be redirected to the current leader
   *
   * @param appendEvent the append entry event
   * @param state       current raft state
   * @return the event result
   */
  override def processAppendEntryEvent(appendEvent: AppendEntryEvent)(node: Membership, state: RaftState): MessageResult = {

    val currentTerm = state.currentTerm.read().getOrElse(0)

    val appendLogResult = for (
      bytes <- serialize(appendEvent.logEntry);
      _     <- Try(state.log.append(currentTerm, bytes)))
    yield {}

    appendLogResult match {
      case Success(_) =>

        val appendEntryRequest = AppendEntriesRequest(
          currentTerm,
          MembershipActor.nodeID,
          state.log.lastLogIndex() - 1,
          state.log.termOf(state.log.lastLogIndex() - 1),
          Seq(appendEvent),
          state.commitIndex
        )

        // Send all followers with up-to-date logs a new AppendEntriesRequest
        val matchingFollowers: Set[RPCTask[RaftMessage]] =
          state.cluster()
            .filter(node => state.leaderState(node.nodeID).matchIndex == state.log.lastLogIndex() - 1)
            .map(RequestTask(appendEntryRequest, _))
            .toSet

        MessageResult(matchingFollowers + ReplyTask(AppendEntryAck(true)), ContinueTimer, None)

      case Failure(exception) =>
        log.error(
          s"Serialization error on append entry ${appendEvent.logEntry.key} and UUID \"${appendEvent.uuid}, on term $currentTerm: " +
            s"${exception.getLocalizedMessage}"
        )
        MessageResult(Set(ReplyTask(AppendEntryAck(false))), ContinueTimer, None)
    }

  }

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

    // Due to things like network partitions, a new leader of higher term may exist. We step down in this case
    val nextRole = determineStepDown(appendReply.currentTerm)(state)
    if (nextRole.contains(Follower)) {
      return MessageResult(Set(), ContinueTimer, nextRole)
    }

    // If successful, we're guaranteed that the follower log is consistent with the leader log, and we need to update
    // the known up-to-dateness
    if (appendReply.success) {
      state.leaderState = state.leaderState.patch(node.nodeID, currentIndexState => LogIndexState(
        currentIndexState.nextIndex + 1,
        currentIndexState.nextIndex
      ))

      // Now that the match index is updated, we can check to see if any entries are majority committed
      val sortedMatchIndexes = state.leaderState.matches().toSeq.sorted
      state.commitIndex = sortedMatchIndexes((sortedMatchIndexes.size - 1) / 2)

      MessageResult(Set(), ResetTimer(RaftIndividualTimeoutKey(node)), None)
    }

    // Otherwise, follower log is inconsistent with leader log, so we roll back one entry and retry the request
    else {
      state.leaderState.patchNextIndex(node.nodeID, _ - 1)
      MessageResult(Set(RequestTask(createAppendEntriesRequest(node.nodeID, state), node)), ContinueTimer, None)
    }
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

  /**
   * Creates an AppendEntriesRequest for a given follower, based on the follower's current log length and match index.
   *
   * @param nodeID the ID of the follower
   * @param state current raft state
   * @return the append entries request body
   */
  def createAppendEntriesRequest(nodeID: String, state: RaftState): AppendEntriesRequest = {

    // Get the next log entry that the follower needs, if it's not caught up
    val logEntries: Try[Seq[LogEntry]] =
      if (state.log.lastLogIndex() < state.leaderState(nodeID).nextIndex) Success(Seq.empty)
      else {
        deserialize(state.log(state.leaderState(nodeID).nextIndex))
          .map(Seq[LogEntry](_))
      }

    // Start building the append request if the log entry could be deserialized
    logEntries match {
      case Success(entries) =>

        val appendEntries = entries.map(AppendEntryEvent(_, None)) // TODO see if this needs uuid tagging
        val followerPrevIndex = state.leaderState(nodeID).nextIndex - 1

        AppendEntriesRequest(
          state.currentTerm.read().getOrElse(0),
          MembershipActor.nodeID,
          followerPrevIndex,
          state.log.termOf(followerPrevIndex),
          appendEntries,
          state.commitIndex
        )

      case Failure(exception) =>
        log.error(s"could not deserialize log entry: ${exception.getLocalizedMessage}")
        throw exception
    }
  }

}
