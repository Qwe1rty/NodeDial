package common.modules.gossip

import akka.grpc.GrpcClientSettings
import akka.stream.Materializer

import scala.concurrent.ExecutionContext


case class GossipKey(nodeID: String, extension: Option[Any] = None)

case class GossipPayload(rpc: GrpcClientSettings => (Materializer, ExecutionContext) => Unit)


object GossipAPI {

  /**
   * Signal the gossip actor to publish a value to other random nodes for some
   * defined number of gossip cycles
   *
   * @param key key associated with publish task
   * @param payload the gRPC payload function to be called on
   */
  case class PublishRequest(key: GossipKey, payload: GossipPayload)
}
