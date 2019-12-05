package common.utils


trait ActorDefaults {

  def receivedUnknown(x: Any): String =
    s"Unknown object of type ${x.getClass.getSimpleName} received"
}
