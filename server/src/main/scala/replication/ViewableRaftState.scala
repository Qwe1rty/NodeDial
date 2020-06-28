package replication


/**
 * ViewableRaftState defines the interface that allows internal sources to
 * read the raft actor's current state in a thread-safe manner, while still
 * having the raft actor be cast as an ActorRef
 */
trait ViewableRaftState {
  this: RaftActor =>

  final def getTerm(): Long = {
    ???
  }

  ??? // TODO the rest
}
