package common.gossip

import membership.api.Membership

import scala.util.Try


private[gossip] object GossipSignal {

  case class SendRPC[KeyType](key: GossipKey[KeyType],
                              randomMemberRequest: Try[Option[Membership]])

  case class ClusterSizeReceived[KeyType](key: GossipKey[KeyType],
                                          payload: GossipPayload,
                                          clusterSizeRequest: Try[Int])
}
