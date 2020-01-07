package common.modules.gossip

object GossipAPI {

  /**
   * Signal the gossip actor to publish a value to other random nodes for some
   * defined number of gossip cycles
   *
   * @param key key associated with publish task
   * @param count number of gossip cycles to publish
   */
  case class Publish(key: Any, count: Int)
}
