import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.javaAgents
import sbt.project

ThisBuild / version := "0.0.0"
ThisBuild / scalaVersion := "2.13.0"


lazy val protobufLibraries = Seq(
  "com.google.api.grpc" % "proto-google-common-protos" % "1.16.0" % "protobuf",
  "com.google.api.grpc" % "grpc-google-common-protos" % "1.16.0" % "protobuf",
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
)

lazy val akkaLibraries = Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.6.0",
  "com.typesafe.akka" %% "akka-stream" % "2.6.0",
)

lazy val loggingLibraries = Seq(
  "com.typesafe.akka" %% "akka-slf4j" % "2.6.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
)

lazy val fileLibraries = Seq(
  "com.github.pathikrit" %% "better-files" % "3.8.0"
)

lazy val grpcLibraryGroup = protobufLibraries ++ akkaLibraries
lazy val coreLibraryGroup = grpcLibraryGroup ++ loggingLibraries


lazy val schema = (project in file("schema"))
  .enablePlugins(AkkaGrpcPlugin, JavaAgent /*ALPN agent*/)
  .settings(
    name := "ChordialSchema",
    libraryDependencies ++= grpcLibraryGroup,
    javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test",
  )
  .disablePlugins(AssemblyPlugin)

lazy val client = (project in file("client"))
  .settings(
    name := "ChordialClient",
    assemblySettings,
    libraryDependencies ++= coreLibraryGroup,
  )
  .dependsOn(schema)

lazy val server = (project in file("server"))
  .settings(
    name := "ChordialServer",
    assemblySettings,
    libraryDependencies ++= coreLibraryGroup ++ fileLibraries
  )
  .dependsOn(schema)


lazy val assemblySettings = assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case n if n.startsWith("application.conf") => MergeStrategy.concat
  case _ => MergeStrategy.first
}