package membership.gossip

import akka.grpc.GrpcClientSettings
import akka.stream.Materializer

import scala.concurrent.ExecutionContext


case class GossipKey[+KeyType](key: KeyType)
case class GossipPayload(rpc: GrpcClientSettings => (Materializer, ExecutionContext) => Unit)


object GossipAPI {

  /**
   * Signal the gossip actor to publish a value to other random nodes for some
   * defined number of gossip cycles
   *
   * @param key key associated with publish task
   * @param payload the gRPC payload function to be called on
   */
  case class PublishRequest[+KeyType](key: GossipKey[KeyType], payload: GossipPayload)
}
