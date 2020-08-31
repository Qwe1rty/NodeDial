package membership.impl

import common.membership.SyncInfo
import membership.Administration
import membership.api.{GetClusterInfo, GetClusterSize, GetRandomNode, GetRandomNodes, GetReadiness, InformationCall}


private[impl] trait InformationCallHandler {
  this: Administration =>

  /**
   * Handle membership info get requests
   */
  def receiveInformationCall: Function[InformationCall, Unit] = {

    case GetReadiness =>
      sender ! readiness

    case GetClusterSize =>
      sender ! membershipTable.size

    case GetClusterInfo =>
      sender ! membershipTable.toSeq.map(SyncInfo(_, None))

    case GetRandomNode(nodeState) =>
      sender ! membershipTable.random(nodeState).lastOption

    case GetRandomNodes(nodeState, number) =>
      sender ! membershipTable.random(nodeState, number)
  }
}
