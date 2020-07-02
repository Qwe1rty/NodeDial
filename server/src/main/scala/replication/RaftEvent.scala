package replication

import membership.api.Membership


/**
 * RaftEvent is any Raft message that has the identity of its origin also
 * included
 *
 * @param node node that produced message
 * @param message the Raft message
 */
case class RaftEvent(node: Membership, message: RaftMessage)


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

