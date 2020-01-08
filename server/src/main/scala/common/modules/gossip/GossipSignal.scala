package common.modules.gossip

import common.modules.membership.Membership

import scala.util.Try


private[gossip] object GossipSignal {

  case class SendRPC(key: GossipKey, member: Try[Option[Membership]])
}
