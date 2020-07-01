package replication.roles

import replication._


case object Leader extends RaftRole {

  def processAppendEntryRequest(appendEntry: AppendEntriesRequest, state: RaftState): (RPCTask[AppendEntriesResult], RaftRole) = {
    ???
  }

  def processRequestVoteRequest(requestVote: RequestVoteRequest, state: RaftState): (RPCTask[RequestVoteResult], RaftRole) = {
    ???
  }
}
