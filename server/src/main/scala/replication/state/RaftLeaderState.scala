package replication.state

import membership.api.Membership
import replication.state.RaftLeaderState.LogIndexState

import scala.collection.View


object RaftLeaderState {

  private[replication] case class LogIndexState(
    nextIndex: Int,
    matchIndex: Int,
  )

  def apply(cluster: View[Membership], logSize: Int): RaftLeaderState = RaftLeaderState(
    cluster
      .map(membership => (membership.nodeID, LogIndexState(logSize, 0)))
      .toMap
  )
}


private[replication] case class RaftLeaderState private(
    private val logIndexState: Map[String, LogIndexState]
  ) {

  def apply(nodeID: String): LogIndexState =
    logIndexState(nodeID)

  def +(newState: (String, LogIndexState)): RaftLeaderState =
    new RaftLeaderState(logIndexState + newState)

  def patch(nodeID: String, f: LogIndexState => LogIndexState): RaftLeaderState = {
    logIndexState.get(nodeID) match {
      case Some(state) => this + (nodeID -> f(state))
      case None =>
        throw new IllegalStateException(s"Follower index state for node $nodeID was not found")
    }
  }

  def patchNextIndex(nodeID: String, f: Int => Int): RaftLeaderState =
    patch(nodeID, currentState => currentState.copy(nextIndex = f(currentState.nextIndex)))

  def patchMatchIndex(nodeID: String, f: Int => Int): RaftLeaderState =
    patch(nodeID, currentState => currentState.copy(matchIndex = f(currentState.matchIndex)))

}
