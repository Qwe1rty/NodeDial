package replication

/**
 * RaftRequest is the common trait that Raft RPC parameters share. Both
 * AppendEntriesRequest and RequestVoteRequest inherit from it, allowing you to
 * pattern match the two
 */
trait RaftRequest


/**
 * RaftResult is the common trait that Raft RPC returns share, much like the
 * RaftRequest trait
 */
trait RaftResult

