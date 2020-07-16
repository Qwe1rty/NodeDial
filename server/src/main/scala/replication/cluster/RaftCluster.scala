package replication.cluster

import common.persistence.{JavaSerializer, PersistentVal}
import membership.api.Membership
import replication.RaftState._

import scala.collection.immutable.HashSet


abstract class RaftCluster(self: Membership) {

  private val raftMembership: PersistentVal[Set[Membership]] =
    new PersistentVal[Set[Membership]](RAFT_DIR/"cluster"/RAFT_STATE_EXTENSION) with JavaSerializer[Set[Membership]]

  private var attemptedQuorum: Set[Membership] = new HashSet[Membership]()

  raftMembership.write(HashSet[Membership](self))


  def registerReply(node: Membership): Unit = {
    if (raftMembership.read().get.contains(node)) {
      attemptedQuorum += node
    }
  }

  def hasQuorum: Boolean = attemptedQuorum.size > (raftMembership.read().get.size / 2)

  def resetQuorum(): Unit = attemptedQuorum = HashSet[Membership]()

}
