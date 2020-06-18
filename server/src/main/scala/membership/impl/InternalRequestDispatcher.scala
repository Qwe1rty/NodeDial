package membership.impl

import membership.MembershipActor
import membership.api.{DeclarationCall, InformationCall, MembershipAPI, SubscriptionCall}


/**
 * This trait implements the membership API for internal requests from other parts
 * of the program.
 *
 * It aggregates the 3 smaller subcategories of API calls under the MembershipAPI
 * definition, which lets the membership module separate internal vs. external
 * requests
 */
private[membership] trait InternalRequestDispatcher
  extends DeclarationCallHandler
  with InformationCallHandler
  with SubscriptionCallHandler {

  this: MembershipActor =>

  def receiveAPICall: Function[MembershipAPI, Unit] = {

    case declarationCall: DeclarationCall =>
      receiveDeclarationCall(declarationCall)

    case informationCall: InformationCall =>
      receiveInformationCall(informationCall)

    case subscriptionCall: SubscriptionCall =>
      receiveSubscriptionCall(subscriptionCall)
  }
}
