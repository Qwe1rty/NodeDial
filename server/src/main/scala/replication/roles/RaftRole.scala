package replication.roles

import replication._


/**
 * RaftRole represents one of the Raft server states (Leader, Candidate, Follower)
 */
trait RaftRole {

  final def processRaftEvent(event: RaftEvent, state: RaftState): (RPCTask[RaftResult], RaftRole) = {
    event.message match {
      case appendEntry: AppendEntriesRequest => processAppendEntry(appendEntry, state)
      case requestVote: RequestVoteRequest   => processRequestVote(requestVote, state)
    }
  }

  def processAppendEntry(appendEntry: AppendEntriesRequest, state: RaftState): (RPCTask[AppendEntriesResult], RaftRole)

  def processRequestVote(requestVote: RequestVoteRequest, state: RaftState): (RPCTask[RequestVoteResult], RaftRole)
}
