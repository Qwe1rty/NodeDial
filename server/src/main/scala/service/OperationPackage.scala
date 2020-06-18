package service

import akka.actor.ActorRef
import schema.RequestTrait


class OperationPackage(
  val requestActor: ActorRef,
  val requestHash:  String,
  val requestBody:  RequestTrait
)
