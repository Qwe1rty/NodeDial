package membership.gossip

import akka.grpc.GrpcClientSettings
import akka.stream.Materializer

import scala.concurrent.ExecutionContext


case class GossipKey[+KeyType](key: KeyType)
case class GossipPayload(rpc: GrpcClientSettings => (Materializer, ExecutionContext) => Unit)

private case class PayloadTracker(payload: GossipPayload, var count: Int, cooldown: Int) {

  def apply(grpcClientSettings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext): Unit = {
    payload.rpc(grpcClientSettings)(mat, ec)
    count -= 1
  }
}