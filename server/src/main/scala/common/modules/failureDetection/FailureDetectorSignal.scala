package common.modules.failureDetection

import common.modules.membership.{Confirmation, Membership}

import scala.util.Try


private[failureDetection] object FailureDetectorSignal {

  case class DirectRequest(potentialTarget: Try[Option[Membership]])
  case class DirectResponse(target: Membership, directResult: Try[Confirmation])

  case class FollowupTrigger(target: Membership)
  case class FollowupRequest(target: Membership, followupTeam: Try[Seq[Membership]])
  case class FollowupResponse(target: Membership, followupResult: Try[Confirmation])


  /**
   * Trigger the failure detector to notify the membership actor that the node is dead.
   * If the membership actor has already received an event stating that the node refuted
   * the suspicion, then this message is discarded
   *
   * @param target the target node
   */
  case class DeclareDeath(target: Membership)
}
