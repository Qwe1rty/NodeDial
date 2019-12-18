package common.modules.membership

import scala.util.Try


private[membership] object FailureDetectorSignal {

  case class DirectResponse(target: Membership, directResult: Try[Confirmation])

  case class FollowupRequest(target: Membership)
  case class FollowupResponse(target: Membership, followupResult: Try[Confirmation])

  case class DeclareDeath(target: Membership)
  case class AbsolveDeath(target: Membership)
}
