package membership.api

import common.membership.SyncResponse
import common.membership.types.NodeState

import scala.util.Try


// Event-related calls
sealed trait DeclarationCall extends MembershipAPI

/**
 * Signals the membership actor that a prerequisite service is ready (essentially a
 * "countdown" for the membership to start join procedure)
 * Does not return anything
 */
case object DeclareReadiness extends DeclarationCall

/**
 * Signals the membership actor to broadcast the declaration across to the other nodes and
 * to internal subscribers.
 * Does not return anything
 *
 * @param nodeState state of the node
 * @param membershipPair node identifier
 */
case class DeclareEvent(nodeState: NodeState, membershipPair: Membership) extends DeclarationCall

private[membership] case class SeedResponse(syncResponse: Try[SyncResponse]) extends DeclarationCall

