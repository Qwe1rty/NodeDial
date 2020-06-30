package replication.roles

import replication._


/**
 * RaftRole represents one of the Raft server states (Leader, Candidate, Follower)
 */
trait RaftRole {

  final def processRaftEvent(event: RaftRequest, state: RaftState): Option[RaftResult] = event match {
    case appendEntry: AppendEntriesRequest => processAppendEntry(appendEntry, state)
    case requestVote: RequestVoteRequest   => processRequestVote(requestVote, state)
  }

  def processAppendEntry(appendEntry: AppendEntriesRequest, state: RaftState): Option[AppendEntriesResult]

  def processRequestVote(requestVote: RequestVoteRequest, state: RaftState): Option[RequestVoteResult]
}
