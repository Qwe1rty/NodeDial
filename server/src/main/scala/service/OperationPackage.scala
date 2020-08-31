package service


/**
 * This class is the service layer's representation of a client request. It is
 * passed down to the partitioning/replication layers to be interpreted, serialized,
 * and repackaged depending on their needs
 *
 * @param requestActor actor that manages this request
 * @param operation the request body
 */
//case class OperationPackage(
//  requestActor: ActorPath,
//  uuid:         UUID,
//  operation:    RequestTrait
//)


