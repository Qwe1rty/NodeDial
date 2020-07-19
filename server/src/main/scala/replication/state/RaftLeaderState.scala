package replication.state


trait RaftLeaderState {

  this: RaftState =>

  private[replication] case class LogIndexState(
    nextIndex: Int,
    matchIndex: Int,
  )

  var logIndexState: Map[String, LogIndexState] = newIndexState()


  def initialize(): Unit =
    logIndexState = newIndexState()

  def updateFollowerIndex(nodeID: String, nextIndex: Int, matchIndex: Int): Unit =
    updateIndexState(nodeID, _ => LogIndexState(nextIndex, matchIndex))

  def updateFollowerMatchIndex(nodeID: String, matchIndex: Int): Unit =
    updateIndexState(nodeID, _.copy(matchIndex = matchIndex))

  def updateFollowerNextIndex(nodeID: String, nextIndex: Int): Unit =
    updateIndexState(nodeID, _.copy(nextIndex = nextIndex))


  private def updateIndexState(nodeID: String, f: LogIndexState => LogIndexState): Unit = {
    logIndexState.get(nodeID) match {
      case Some(state) => logIndexState += nodeID -> f(state)
      case None =>
        throw new IllegalStateException(s"Tracked follower index stte for node $nodeID was not found")
    }
  }

  private def newIndexState(): Map[String, LogIndexState] =
    cluster()
      .map(membership => (membership.nodeID, LogIndexState(replicatedLog.size(), 0)))
      .toMap

}
