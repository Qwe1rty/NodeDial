package replication.state

import administration.Membership
import com.risksense.ipaddr.IpAddress
import common.persistence.{JavaSerializer, PersistentVal}
import replication.Raft
import replication.state.RaftState._

import scala.collection.View
import scala.collection.immutable.{HashMap, HashSet}


abstract class RaftCluster(self: Membership) {

  /**
   * Map the nodeID -> numerical IP address, since the IP address object does not implement
   * the Java serializable interface
   */
  private val raftMembership: PersistentVal[HashMap[String, Long]] =
    new PersistentVal[HashMap[String, Long]](Raft.RAFT_DIR/("cluster" + RAFT_STATE_EXTENSION))
        with JavaSerializer[HashMap[String, Long]]

  raftMembership.write(HashMap[String, Long](
    self.nodeID -> self.ipAddress.numerical
  ))

  /**
   * A global quorum that is used to determine if a Candidate has won an election
   */
  private var electionQuorum: Set[String] = new HashSet[String]()

  private def get: Iterable[Membership] = raftMembership.read().get.map {
    case (nodeID, ipAddress) => Membership(nodeID, IpAddress(ipAddress))
  }


  def foreach(f: Membership => Unit): Unit =
  get.foreach(f)

  def cluster(): View[Membership] =
  get.view

  def iterator(): Iterator[Membership] =
  get.iterator

  def clusterSize(): Int =
  raftMembership.read().get.size

  def member(nodeID: String): Membership =
    Membership(nodeID, IpAddress(raftMembership.read().get.apply(nodeID)))

  def isMember(nodeID: String): Boolean =
    raftMembership.read().get.contains(nodeID)


  def registerReply(nodeID: String): Unit = if (raftMembership.read().get.contains(nodeID)) {
    electionQuorum += nodeID
  }

  def hasQuorum: Boolean =
    electionQuorum.size > (raftMembership.read().get.size / 2)

  def resetQuorum(): Unit =
    electionQuorum = HashSet[String](self.nodeID)

  def numReplies(): Int =
    electionQuorum.size
}
