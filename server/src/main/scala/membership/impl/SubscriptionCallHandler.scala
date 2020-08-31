package membership.impl

import membership.Administration
import membership.api.{Subscribe, SubscriptionCall, Unsubscribe}


private[impl] trait SubscriptionCallHandler {
  this: Administration =>

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
