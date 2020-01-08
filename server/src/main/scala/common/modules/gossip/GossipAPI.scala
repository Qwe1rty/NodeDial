package common.modules.gossip

import akka.grpc.GrpcClientSettings
import akka.stream.Materializer

import scala.concurrent.ExecutionContext


// TODO figure out what these key/val mappings need to be

case class GossipKey(nodeID: String, extension: Option[Any] = None)

case class GossipPayload(payload: GrpcClientSettings => (Materializer, ExecutionContext) => Unit)

//trait GossipPayload {
//
//  def apply(grpcClientSettings: GrpcClientSettings)(implicit mat: Materializer, ex: ExecutionContext)
//}


object GossipAPI {

  /**
   * Signal the gossip actor to publish a value to other random nodes for some
   * defined number of gossip cycles
   *
   * @param key key associated with publish task
   * @param count number of gossip cycles to publish
   */
  case class PublishRequest(key: GossipKey, payload: GossipPayload, count: Int)
}
