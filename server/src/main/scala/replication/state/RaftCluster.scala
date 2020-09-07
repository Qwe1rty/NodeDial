package replication.state

import administration.Membership
import com.risksense.ipaddr.IpAddress
import common.persistence.{JavaSerializer, PersistentVal}
import replication.ClusterEntry.ClusterState
import replication.state.RaftState._
import replication.{ClusterReconfigEvent, Raft}

import scala.collection.View
import scala.collection.immutable.{HashMap, HashSet}


class RaftCluster(self: Membership) {

  // Map the nodeID -> numerical IP address, since the IP address object does not implement
  // the Java serializable interface
  private val currentMembership: PersistentVal[HashMap[String, Long]] =
    new PersistentVal[HashMap[String, Long]](Raft.RAFT_DIR/("cluster" + RAFT_STATE_EXTENSION))
        with JavaSerializer[HashMap[String, Long]]

  currentMembership.write(HashMap[String, Long](
    self.nodeID -> self.ipAddress.numerical
  ))

  private var currentClusterState: ClusterState = ClusterState.STABLE
  private var transitionalMembership: HashMap[String, Long] = HashMap.empty

  private var attemptedQuorum: Set[String] = HashSet.empty

  private def tupleToMembership: Function[(String, Long), Membership] = {
    case (nodeID, ipAddress) => Membership(nodeID, IpAddress(ipAddress))
  }

  private def membershipToTuple: Function[Membership, (String, Long)] =
    membership => membership.nodeID -> membership.ipAddress.numerical


  // Methods for accessing cluster info
  def member(nodeID: String): Membership =
    Membership(nodeID, IpAddress(currentMembership.read().get.apply(nodeID)))

  def foreach(f: Membership => Unit): Unit =
    getCurrentMembership.foreach(f)

  def cluster(): View[Membership] = // TODO change to union on transitioning phase
    getCurrentMembership.view

  def iterator(): Iterator[Membership] =
    getCurrentMembership.iterator

  def currentClusterSize(): Int =
    currentMembership.read().get.size

  def transitionalClusterSize(): Int =
    transitionalMembership.size


  // Methods for cluster configuration changes
  def transitionMembership(transitional: Iterable[Membership]): Unit = {
    currentClusterState = ClusterState.TRANSITIONING
    transitionalMembership = HashMap.from(transitional.map(membershipToTuple))
  }

  def updateMembership(): Unit = {
    if (currentClusterState != ClusterState.TRANSITIONING) {
      throw new IllegalStateException("Cannot update membership, not currently in a transitioning state")
    }

    currentClusterState = ClusterState.STABLE
    currentMembership.write(transitionalMembership)
  }

  def getClusterState: ClusterState =
    currentClusterState

  def getCurrentMembership: Iterable[Membership] =
    currentMembership.read().get.map(tupleToMembership)

  def getTransitionalMembership: Iterable[Membership] =
    transitionalMembership.map(tupleToMembership)


  // Quorum-related methods
  def registerReply(nodeID: String): Unit =
    if (currentMembership.read().get.contains(nodeID)) attemptedQuorum += nodeID

  def hasQuorum: Boolean = getClusterState match {
    case ClusterState.Unrecognized(value) => throw new IllegalStateException(s"Unknown cluster state: $value")
    case ClusterState.STABLE              => attemptedQuorum.size > (currentClusterSize() / 2)
    case ClusterState.TRANSITIONING       =>
      currentMembership.read().get.keySet.intersect(attemptedQuorum).size > (currentClusterSize() / 2) &&
      transitionalMembership.keySet.intersect(attemptedQuorum).size > (transitionalClusterSize() / 2)
  }

  def resetQuorum(): Unit =
    attemptedQuorum = HashSet[String](self.nodeID)

  def numReplies(): Int =
    attemptedQuorum.size
}
