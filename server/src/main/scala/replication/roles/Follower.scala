package replication.roles
import replication.{AppendEntriesRequest, AppendEntriesResult, RaftState, RequestVoteRequest, RequestVoteResult}


case object Follower extends RaftRole {

  override def processAppendEntry(appendEntry: AppendEntriesRequest, state: RaftState): (Option[AppendEntriesResult], RaftRole) = ???

  override def processRequestVote(requestVote: RequestVoteRequest, state: RaftState): (Option[RequestVoteResult], RaftRole) = ???
}
