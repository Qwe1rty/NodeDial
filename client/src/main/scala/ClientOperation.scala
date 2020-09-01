/**
 * The ClientOperation trait, along with its children, allows for easy enumeration
 * of the different operations as a field of the ClientHandler builder object
 *
 * Unfortunately, Akka gRPC does not automatically generate this enumeration
 */
sealed trait ClientOperation

case object UNSPECIFIED extends ClientOperation

case object GET extends ClientOperation
case object POST extends ClientOperation
case object DELETE extends ClientOperation

case object READY extends ClientOperation