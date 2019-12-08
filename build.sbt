import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.javaAgents
import sbt.project

ThisBuild / version := "1.0.0"
ThisBuild / scalaVersion := "2.12.0"


lazy val dependencies =
  new {
    val protoCommonV = "1.16.0"
    val grpcCommonV = "1.16.0"
    val scalaProtoV = scalapb.compiler.Version.scalapbVersion

    val akkaActorV = "2.6.0"
    val akkaStreamV = "2.6.0"
    val akkaSLF4jV = "2.6.0"

    val logbackV = "1.2.3"
    val betterFilesV = "3.8.0"
    val hasherV = "1.2.2"


    val protoCommon =  "com.google.api.grpc"  % "proto-google-common-protos" % protoCommonV % "protobuf"
    val grpcCommon  = "com.google.api.grpc"   % "grpc-google-common-protos"  % grpcCommonV  % "protobuf"
    val scalaProto  = "com.thesamet.scalapb" %% "scalapb-runtime"            % scalaProtoV  % "protobuf"

    val akkaActor   = "com.typesafe.akka"    %% "akka-actor"                 % akkaActorV
    val akkaStream  = "com.typesafe.akka"    %% "akka-stream"                % akkaStreamV
    val akkaSLF4j   = "com.typesafe.akka"    %% "akka-slf4j"                 % akkaSLF4jV

    val logback     = "ch.qos.logback"        % "logback-classic"            % logbackV
    val betterFiles = "com.github.pathikrit" %% "better-files"               % betterFilesV
    val hasher      = "com.outr"             %% "hasher"                     % hasherV
  }

lazy val grpcLibraryGroup = Seq(
  dependencies.protoCommon,
  dependencies.grpcCommon,
  dependencies.scalaProto
)

lazy val coreLibraryGroup = Seq(
  dependencies.akkaSLF4j,
  dependencies.logback
)

lazy val jettyAgent = "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test"


lazy val schema = (project in file("schema"))
  .enablePlugins(AkkaGrpcPlugin, JavaAgent /*ALPN agent*/)
  .settings(
    name := "ChordialSchema",
    libraryDependencies ++= grpcLibraryGroup,
    javaAgents += jettyAgent
  )
  .disablePlugins(AssemblyPlugin)

lazy val root = (project in file("."))
  .aggregate(client, server)
  .disablePlugins(AssemblyPlugin)


lazy val client = (project in file("client"))
  .enablePlugins(AkkaGrpcPlugin, JavaAgent /*ALPN agent*/)
  .settings(
    name := "ChordialClient",
    assemblySettings,
    libraryDependencies ++= coreLibraryGroup,
    javaAgents += jettyAgent
  )
  .dependsOn(schema)

lazy val server = (project in file("server"))
  .enablePlugins(AkkaGrpcPlugin, JavaAgent /*ALPN agent*/)
  .settings(
    name := "ChordialServer",
    assemblySettings,
    libraryDependencies ++= coreLibraryGroup ++ Seq(
      dependencies.betterFiles,
      dependencies.hasher
    ),
    javaAgents += jettyAgent
  )
  .dependsOn(schema)


lazy val assemblySettings = assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case n if n.startsWith("application.conf") => MergeStrategy.concat
  case n if n.startsWith("reference.conf") => MergeStrategy.concat
  case _ => MergeStrategy.first
}