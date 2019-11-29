name := "Chordial"
version := "0.0.0"
scalaVersion := "2.13.0"

libraryDependencies ++= Seq(
  "com.google.api.grpc" % "proto-google-common-protos" % "1.16.0" % "protobuf",
  "com.google.api.grpc" % "grpc-google-common-protos" % "1.16.0" % "protobuf",
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
//  "com.thesamet.scalapb" %% "compilerplugin" % "0.9.4" % "protobuf",
  "com.typesafe.akka" %% "akka-actor" % "2.6.0",
  "com.typesafe.akka" %% "akka-remote" % "2.6.0",
  "com.typesafe.akka" %% "akka-slf4j" % "2.6.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.github.pathikrit" %% "better-files" % "3.8.0"
)

enablePlugins(AkkaGrpcPlugin)
enablePlugins(JavaAgent) // ALPN agent

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case n if n.startsWith("application.conf") => MergeStrategy.concat
  case _ => MergeStrategy.first
}