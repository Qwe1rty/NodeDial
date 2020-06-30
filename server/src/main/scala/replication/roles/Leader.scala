package replication.roles

import replication._


case object Leader extends RaftRole {

  def processAppendEntry(appendEntry: AppendEntriesRequest, state: RaftState): Option[AppendEntriesResult] = {
    ???
  }

  def processRequestVote(requestVote: RequestVoteRequest, state: RaftState): Option[RequestVoteResult] = {
    ???
  }
}
