package server.service

import com.google.protobuf.ByteString

// This trait is to allow for key/value retrieval from all the protobuf generated request types
//
// See the "Custom base traits for messages" section of https://scalapb.github.io/customizations.html
// for more info

trait RequestTrait {
  def key: Option[String]
  def value: Option[ByteString]
}
