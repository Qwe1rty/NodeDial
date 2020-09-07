package administration.gossip

import akka.grpc.GrpcClientSettings
import akka.stream.Materializer

import scala.concurrent.ExecutionContext


/**
 * The key associated with a certain value to gossip
 *
 * @param key the key
 * @tparam KeyType the key type
 */
case class GossipKey[+KeyType](key: KeyType)

/**
 *  The callback function that's called to send the message out to other nodes
 *
 * @param rpc the RPC function that's called
 */
case class GossipPayload(rpc: GrpcClientSettings => (Materializer, ExecutionContext) => Unit)

private case class PayloadTracker(payload: GossipPayload, var count: Int, cooldown: Int) {

  def apply(grpcClientSettings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext): Unit = {
    payload.rpc(grpcClientSettings)(mat, ec)
    count -= 1
  }
}