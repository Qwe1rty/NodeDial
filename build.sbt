name := "Chordial"
version := "0.0.0"
scalaVersion := "2.13.0"

libraryDependencies ++= Seq(
  "com.google.api.grpc" % "proto-google-common-protos" % "1.16.0" % "protobuf",
  "com.google.api.grpc" % "grpc-google-common-protos" % "1.16.0" % "protobuf",
  "com.typesafe.akka" %% "akka-actor" % "2.5.26",
  "com.typesafe.akka" %% "akka-remote" % "2.5.26",
)

enablePlugins(AkkaGrpcPlugin)
enablePlugins(JavaAgent) // ALPN agent

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test"