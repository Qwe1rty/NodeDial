package server.service

// This trait is to allow for key/value retrieval from all the protobuf generated response types
//
// See the "Custom base traits for messages" section of https://scalapb.github.io/customizations.html
// for more info

trait ResponseTrait {
  def value: Option[String]
}
