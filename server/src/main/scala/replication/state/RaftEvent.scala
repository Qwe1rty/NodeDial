package replication.state


// NOTE: The below traits are inherited by the Raft generated protobuf
// messages!!

/**
 * RaftMessage is the parent trait for all Raft messages
 */
trait RaftMessage


/**
 * RaftRequest is the common trait that Raft RPC parameters share. Both
 * AppendEntriesRequest and RequestVoteRequest inherit from it, allowing you to
 * pattern match the two
 */
trait RaftRequest extends RaftMessage


/**
 * RaftResult is the common trait that Raft RPC returns share, much like the
 * RaftRequest trait
 */
trait RaftResult extends RaftMessage

