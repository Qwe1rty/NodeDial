package replication.roles

import replication._


case object Leader extends RaftRole {

  def processAppendEntry(appendEntry: AppendEntriesRequest, state: RaftState): (RPCTask[AppendEntriesResult], RaftRole) = {
    ???
  }

  def processRequestVote(requestVote: RequestVoteRequest, state: RaftState): (RPCTask[RequestVoteResult], RaftRole) = {
    ???
  }
}
