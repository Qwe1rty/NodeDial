package replication.state

import administration.Membership
import common.persistence.{JavaSerializer, PersistentVal}
import replication.state.RaftState._

import scala.collection.View
import scala.collection.immutable.HashSet


abstract class RaftCluster(self: Membership) {

  private val raftMembership: PersistentVal[HashSet[Membership]] =
    new PersistentVal[HashSet[Membership]](RAFT_DIR/"cluster"/RAFT_STATE_EXTENSION) with JavaSerializer[HashSet[Membership]]

  private var attemptedQuorum: Set[Membership] = new HashSet[Membership]()

  raftMembership.write(HashSet[Membership](self))


  def foreach(f: Membership => Unit): Unit =
    raftMembership.read().get.foreach(f)

  def cluster(): View[Membership] =
    raftMembership.read().get.view

  def iterator(): Iterator[Membership] =
    raftMembership.read().get.iterator

  def registerReply(node: Membership): Unit = {
    if (raftMembership.read().get.contains(node)) {
      attemptedQuorum += node
    }
  }

  def hasQuorum: Boolean =
    attemptedQuorum.size > (raftMembership.read().get.size / 2)

  def resetQuorum(): Unit =
    attemptedQuorum = HashSet[Membership](self)

  def numReplies(): Int =
    attemptedQuorum.size
}
