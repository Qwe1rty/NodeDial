package replication.roles
import replication.{AppendEntriesRequest, AppendEntriesResult, RaftState, RequestVoteRequest, RequestVoteResult}


case object Follower extends RaftRole {

  override def processAppendEntryRequest(appendEntry: AppendEntriesRequest, state: RaftState): (Option[AppendEntriesResult], RaftRole) = ???

  override def processRequestVoteRequest(requestVote: RequestVoteRequest, state: RaftState): (Option[RequestVoteResult], RaftRole) = ???
}
