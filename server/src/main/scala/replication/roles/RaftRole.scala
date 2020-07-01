package replication.roles

import replication._


/**
 * RaftRole represents one of the Raft server states (Leader, Candidate, Follower)
 */
trait RaftRole {

  /** The output that contains information about what RPC actions need to be done, and next role state */
  type EventResult = (Option[RPCTask[RaftMessage]], RaftRole)

  /** Used for logging */
  val roleName: String

  /** Ingest a Raft event and return the event result */
  final def processRaftEvent(event: RaftEvent, state: RaftState): EventResult = {
    (event.message match {
      case appendEntry: AppendEntryEvent     => processAppendEntryEvent(appendEntry) _
      case appendEntry: AppendEntriesRequest => processAppendEntryRequest(appendEntry) _
      case requestVote: RequestVoteRequest   => processRequestVoteRequest(requestVote) _
      case appendEntry: AppendEntriesResult  => processAppendEntryResult(appendEntry) _
      case requestVote: RequestVoteResult    => processRequestVoteResult(requestVote) _
    })(state)
  }

  def processAppendEntryEvent(appendEntry: AppendEntryEvent)(state: RaftState): EventResult

  def processAppendEntryRequest(appendEntry: AppendEntriesRequest)(state: RaftState): EventResult

  def processRequestVoteRequest(requestVote: RequestVoteRequest)(state: RaftState): EventResult

  def processAppendEntryResult(appendEntry: AppendEntriesResult)(state: RaftState): EventResult

  def processRequestVoteResult(requestVoe: RequestVoteResult)(state: RaftState): EventResult
}
