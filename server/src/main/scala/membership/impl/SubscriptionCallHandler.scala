package membership.impl

import membership.MembershipActor
import membership.api.{Subscribe, SubscriptionCall, Unsubscribe}


private[impl] trait SubscriptionCallHandler {
  this: MembershipActor =>

  /**
   * Handle subscription requests for observers to be informed of membership changes
   */
  def receiveSubscriptionCall: Function[SubscriptionCall, Unit] = {

    case Subscribe(actorRef) =>
      subscribers += actorRef

    case Unsubscribe(actorRef) =>
      subscribers -= actorRef
  }
}
