package common.modules.gossip


case class GossipKey(nodeID: String, extension: Option[Any]) // TODO figure out what this needs to be


object GossipAPI {

  /**
   * Signal the gossip actor to publish a value to other random nodes for some
   * defined number of gossip cycles
   *
   * @param key key associated with publish task
   * @param count number of gossip cycles to publish
   */
  case class PublishRequest(key: GossipKey, count: Int)
}
