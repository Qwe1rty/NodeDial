package membership.impl

import akka.grpc.GrpcClientSettings
import akka.stream.ActorMaterializer
import common.membership.Event.{Failure, Join, Suspect}
import common.membership.types.NodeState
import common.membership.{Event, FullSyncRequest, MembershipServiceClient}
import membership.MembershipActor
import membership.MembershipActor.nodeID
import membership.api.{DeclarationCall, DeclareEvent, DeclareReadiness, SeedResponse}
import partitioning.PartitionHashes
import schema.ImplicitDataConversions._
import schema.PortConfiguration.MEMBERSHIP_PORT

import scala.concurrent.ExecutionContext
import scala.util.Success


/**
 * Handle membership-altering requests from other components of the local node, that become
 * broadcasted to the rest of the cluster
 */
private[impl] trait DeclarationCallHandler {
  this: MembershipActor =>

  def receiveDeclarationCall: Function[DeclarationCall, Unit] = {

    case DeclareReadiness =>

      log.info("Membership readiness signal received")
      initializationCount -= 1

      if (initializationCount <= 0 && !readiness) {
        log.debug("Starting initialization sequence to establish readiness")

        // Only if the seed node is defined will there be any synchronization calls
        clusterAddresses.seedIP match {

          case Some(seedIP) =>
            if (seedIP != clusterAddresses.selfIP) {
              log.info("Contacting seed node for membership listing")

              implicit val ec: ExecutionContext = actorSystem.dispatcher

              val grpcClientSettings = GrpcClientSettings.connectToServiceAt(
                seedIP,
                MEMBERSHIP_PORT
              )

              MembershipServiceClient(grpcClientSettings)(ActorMaterializer()(context), ec)
                .fullSync(FullSyncRequest(nodeID, clusterAddresses.selfIP))
                .onComplete(self ! SeedResponse(_))
            }
            else {
              readiness = true
              log.info("Seed IP was the same as this current node's IP, no full sync necessary")
            }

          case None =>
            readiness = true
            log.info("No seed node specified, will assume single-node cluster readiness")
        }
      }

    case DeclareEvent(nodeState, membershipPair) => {

      val targetID = membershipPair.nodeID
      val version = membershipTable.versionOf(targetID)
      log.info(s"Declaring node ${targetID} according to detected state ${nodeState}")

      val eventCandidate: Option[Event] = nodeState match {
        case NodeState.SUSPECT => Some(Event(targetID).withSuspect(Suspect(version)))
        case NodeState.DEAD =>    Some(Event(targetID).withFailure(Failure(version)))
        case _ =>                 None
      }

      eventCandidate.foreach(event => {
        publishExternally(event)
        publishInternally(event)
      })
    }

    case SeedResponse(syncResponse) => syncResponse match {

      case Success(response) =>
        membershipTable ++= response.syncInfo.map(_.nodeInfo)

        readiness = true
        log.info("Successful full sync response received from seed node")

        publishExternally(Event(nodeID).withJoin(Join(clusterAddresses.selfIP, PartitionHashes(Nil))))
        log.info("Broadcasting join event to other nodes")

      case scala.util.Failure(e) =>
        log.error(s"Was unable to retrieve membership info from seed node: ${e}")

        self ! DeclareReadiness
        log.error("Attempting to reconnect with seed node")
    }
  }

}
