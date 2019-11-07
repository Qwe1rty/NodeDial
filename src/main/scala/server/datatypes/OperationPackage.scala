package server.datatypes

import akka.actor.ActorRef


class OperationPackage(val requestActor: ActorRef, val requestHash: String, val requestBody: RequestTrait)
