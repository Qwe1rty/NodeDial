sealed trait ExitStatus {
  def code: Int
}

object ExitStatus {

  implicit def statusToCode(exitStatus: ExitStatus): Int =
    exitStatus.code
}


case object STATUS_OK extends ExitStatus {
  override def code: Int = 0
}

case object PARSE_ERROR extends ExitStatus {
  override def code: Int = 1
}

case object CLIENT_INTERNAL_ERROR extends ExitStatus {
  override def code: Int = 2
}

case object SERVER_INTERNAL_ERROR extends ExitStatus {
  override def code: Int = 3
}

case object GRPC_RESPONSE_ERROR extends ExitStatus {
  override def code: Int = 4
}

case object UNIMPLEMENTED_ERROR extends ExitStatus {
  override def code: Int = 5
}
