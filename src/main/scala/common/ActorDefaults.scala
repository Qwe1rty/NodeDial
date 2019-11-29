package common


trait ActorDefaults {

  def unknownTypeMessage(x: Any): String =
    s"Unknown object of type ${x.getClass.getSimpleName} received"
}
