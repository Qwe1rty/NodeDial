package membership.api

import akka.actor.ActorRef


// Subscription calls
private[membership] sealed trait SubscriptionCall extends MembershipAPI

/**
 * Registers an actor to receive incoming event updates from the membership module
 *
 * @param actorRef actor reference
 */
case class Subscribe(actorRef: ActorRef) extends SubscriptionCall

object Subscribe {

  def apply()(implicit actorRef: ActorRef, d: SubscriptionCall.Disambiguate.type): Subscribe =
    Subscribe(actorRef)
}

/**
 * Removes an actor from the membership module's event update list
 *
 * @param actorRef actor reference
 */
case class Unsubscribe(actorRef: ActorRef) extends SubscriptionCall

object Unsubscribe {

  def apply()(implicit actorRef: ActorRef, d: SubscriptionCall.Disambiguate.type): Unsubscribe =
    Unsubscribe(actorRef)
}

private object SubscriptionCall {

  /**
   * An object that allows for the creation of the Subscribe and Unsubscribe objects through
   * implicit passing of the "self" field in an actor
   *
   * Since the companion object's "apply" function appears the same as the class constructors
   * after type erasure, this ensures that they are actually different as there's effectively
   * a new parameter
   */
  implicit object Disambiguate
}

