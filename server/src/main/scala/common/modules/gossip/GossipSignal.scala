package common.modules.gossip

import common.modules.membership.Membership

import scala.util.Try


private[gossip] object GossipSignal {

  case class ClusterSizeReceived(key: GossipKey, payload: GossipPayload, clusterSizeRequest: Try[Int])
  case class SendRPC(key: GossipKey, randomMemberRequest: Try[Option[Membership]])
}
