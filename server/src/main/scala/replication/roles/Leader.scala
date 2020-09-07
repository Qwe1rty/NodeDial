package replication.roles

import administration.Administration
import common.rpc.{RPCTask, ReplyTask, RequestTask}
import common.time.{ContinueTimer, ResetTimer}
import org.slf4j.{Logger, LoggerFactory}
import replication.LogEntry.EntryType.Data
import replication._
import replication.roles.RaftRole.MessageResult
import replication.state.RaftLeaderState.LogIndexState
import replication.state.{RaftIndividualTimeoutKey, RaftMessage, RaftState}

import scala.util.{Failure, Success, Try}


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
   * @param nodeID  the node that timed out
   * @param state current raft state
   * @return the timeout result
   */
  override def processRaftIndividualTimeout(nodeID: String)(state: RaftState)(implicit log: Logger): MessageResult = {

    // For leaders, individual timeouts mean a node has not received a heartbeat/request in a while
    val requestTask: Set[RPCTask[RaftMessage]] =
      if (nodeID == Administration.nodeID) {
        log.debug("Individual leader timeout reached for self, will not perform any RPC action")
        Set.empty
      }
      else {
        log.info(s"Individual leader timeout reached for node $nodeID")
        Set(RequestTask(createAppendEntriesRequest(nodeID, state), nodeID))
      }

    MessageResult(requestTask, ContinueTimer, None)
  }

  /**
   * Handle a direct append entry request received by this server. Only in the leader role is this
   * actually processed - otherwise it should be redirected to the current leader
   *
   * @param appendEvent the append entry event
   * @param state       current raft state
   * @return the event result
   */
  override def processAppendEntryEvent(appendEvent: AppendEntryEvent)(state: RaftState)(implicit log: Logger): MessageResult = {

    val currentTerm: Long = state.currentTerm.read().getOrElse(0)

    val appendLogResult = for (
      logEntryBytes <- Raft.LogEntrySerializer.serialize(appendEvent.logEntry);
      appendResult  <- Try(state.log.append(currentTerm, logEntryBytes))
    ) yield appendResult

    appendLogResult match {
      case Success(_) =>

        // Leader doesn't make a network call to itself so it has to manually update its log index record
        state.leaderState = state.leaderState.patch(Administration.nodeID, _ => LogIndexState(
          state.log.lastLogIndex() + 1,
          state.log.lastLogIndex(),
        ))
        if (state.clusterSize() == 1) state.commitIndex += 1

//        appendEvent.logEntry.entryType match {
//          case Data(DataEntry(key, value)) =>
//
//        }

        val appendEntryRequest = AppendEntriesRequest(
          currentTerm,
          Administration.nodeID,
          state.log.lastLogIndex() - 1,
          state.log.termOf(state.log.lastLogIndex() - 1),
          Seq(appendEvent),
          state.commitIndex
        )

        // Send all followers with up-to-date logs a new AppendEntriesRequest
        val matchingFollowers: Set[RPCTask[RaftMessage]] =
          state.cluster()
            .filter(node => node.nodeID != Administration.nodeID)
            .filter(node => state.leaderState(node.nodeID).matchIndex == state.log.lastLogIndex() - 1)
            .map(membership => RequestTask(appendEntryRequest, membership.nodeID))
            .toSet

        MessageResult(matchingFollowers + ReplyTask(AppendEntryAck(success = true)), ContinueTimer, None)

      case Failure(exception) =>
        log.error(
          s"Serialization error on append entry ${appendEvent.logEntry.key} and UUID ${appendEvent.uuid}, on term $currentTerm: " +
            exception.getLocalizedMessage
        )
        MessageResult(Set(ReplyTask(AppendEntryAck(success = false))), ContinueTimer, None)
    }

  }

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
  override def processAppendEntryResult(appendReply: AppendEntriesResult)(state: RaftState)(implicit log: Logger): MessageResult = {

    // Due to things like network partitions, a new leader of higher term may exist. We step down in this case
    val nextRole = determineStepDown(appendReply.currentTerm)(state)
    if (nextRole.contains(Follower)) {
      return MessageResult(Set.empty, ContinueTimer, nextRole)
    }

    // If successful, we're guaranteed that the follower log is consistent with the leader log, and we need to update
    // the known up-to-dateness
    if (appendReply.success) {
      state.leaderState = state.leaderState.patch(appendReply.followerId, currentIndexState => LogIndexState(
        currentIndexState.nextIndex + 1,
        currentIndexState.nextIndex
      ))

      // Now that the match index is updated, we can check to see if any entries are majority committed
      val sortedMatchIndexes = state.leaderState.matches().toSeq.sorted
      state.commitIndex = sortedMatchIndexes((sortedMatchIndexes.size - 1) / 2)

      MessageResult(Set.empty, ResetTimer(RaftIndividualTimeoutKey(appendReply.followerId)), None)
    }

    // Otherwise, follower log is inconsistent with leader log, so we roll back one entry and retry the request
    else {
      state.leaderState.patchNextIndex(appendReply.followerId, _ - 1)
      MessageResult(Set(RequestTask(createAppendEntriesRequest(appendReply.followerId, state), appendReply.followerId)), ContinueTimer, None)
    }
  }

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
   * @param state current raft state
   * @return the event result
   */
  override def processRequestVoteResult(voteReply: RequestVoteResult)(state: RaftState)(implicit log: Logger): MessageResult =
    Follower.processRequestVoteResult(voteReply)(state)

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
        Raft.LogEntrySerializer.deserialize(state.log(state.leaderState(nodeID).nextIndex)).map(Seq[LogEntry](_))
      }

    // Start building the append request if the log entry could be deserialized
    logEntries match {
      case Success(entries) =>

        val appendEntries = entries.map(AppendEntryEvent(_, None)) // TODO see if this needs uuid tagging
        val followerPrevIndex = state.leaderState(nodeID).nextIndex - 1

        AppendEntriesRequest(
          state.currentTerm.read().getOrElse(0),
          Administration.nodeID,
          followerPrevIndex,
          state.log.termOf(followerPrevIndex),
          appendEntries,
          state.commitIndex
        )

      case Failure(exception) =>
        log.error(s"Could not deserialize log entry: ${exception.getLocalizedMessage}")
        throw exception
    }
  }

}
