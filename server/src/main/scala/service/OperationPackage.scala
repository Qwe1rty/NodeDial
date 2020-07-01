package service

import akka.actor.ActorPath
import schema.RequestTrait


/**
 * This class is the service layer's representation of a client request. It is
 * passed down to the partitioning/replication layers to be interpreted, serialized,
 * and repackaged depending on their needs
 *
 * @param requestActor actor that manages this request
 * @param requestHash the hashed request key
 * @param requestBody the request body
 */
case class OperationPackage(
  requestActor: ActorPath,
  requestHash:  String,
  requestBody:  RequestTrait
)


