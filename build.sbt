import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.javaAgents
import sbt.project

ThisBuild / version := "2.0.0"
ThisBuild / scalaVersion := "2.13.1"


lazy val dependencies =
  new {
    val protoCommonV = "1.16.0"
    val grpcCommonV = "1.16.0"
    val scalaProtoV = scalapb.compiler.Version.scalapbVersion
    val ipAddressesV = "1.0.2"

    val akkaActorV = "2.6.0"
    val akkaStreamV = "2.6.0"
    val akkaSLF4jV = "2.6.0"

    val logbackV = "1.2.3"
    val betterFilesV = "3.8.0"
    val hasherV = "1.2.2"
    val scoptV = "3.7.1"


    val protoCommon = "com.google.api.grpc"   % "proto-google-common-protos" % protoCommonV % "protobuf"
    val grpcCommon  = "com.google.api.grpc"   % "grpc-google-common-protos"  % grpcCommonV  % "protobuf"
    val scalaProto  = "com.thesamet.scalapb" %% "scalapb-runtime"            % scalaProtoV  % "protobuf"
    val ipAddresses = "com.risksense"         % "ipaddr_2.12"                % ipAddressesV

    val akkaActor   = "com.typesafe.akka"    %% "akka-actor"                 % akkaActorV
    val akkaStream  = "com.typesafe.akka"    %% "akka-stream"                % akkaStreamV
    val akkaSLF4j   = "com.typesafe.akka"    %% "akka-slf4j"                 % akkaSLF4jV

    val logback     = "ch.qos.logback"        % "logback-classic"            % logbackV
    val betterFiles = "com.github.pathikrit" %% "better-files"               % betterFilesV
    val hasher      = "com.outr"             %% "hasher"                     % hasherV
    val scopt       = "com.github.scopt"     %% "scopt"                      % scoptV
  }

lazy val grpcLibraryGroup = Seq(
  dependencies.protoCommon,
  dependencies.grpcCommon,
  dependencies.scalaProto,
  dependencies.ipAddresses
)

lazy val loggingLibraryGroup = Seq(
  dependencies.akkaSLF4j,
  dependencies.logback
)

lazy val jettyAgent = "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test"


lazy val root = (project in file("."))
  .aggregate(client, server)
  .disablePlugins(AssemblyPlugin)

lazy val api = (project in file("api"))
  .enablePlugins(
    AkkaGrpcPlugin,
    JavaAgent /*ALPN agent*/
  )
  .settings(
    name := "ChordialSchema",
    javaAgents += jettyAgent,
    libraryDependencies ++= grpcLibraryGroup
  )
  .disablePlugins(AssemblyPlugin)


lazy val client = (project in file("client"))
  .enablePlugins(
    AkkaGrpcPlugin,
    JavaAppPackaging,
    JavaAgent /*ALPN agent*/
  )
  .settings(
    name := "ChordialClient",
    assemblySettings,
    javaAgents += jettyAgent,
    libraryDependencies ++= Seq(
      dependencies.scopt
    )
  )
  .dependsOn(api)

lazy val server = (project in file("server"))
  .enablePlugins(
    AkkaGrpcPlugin,
    JavaAppPackaging,
    JavaAgent /*ALPN agent*/
  )
  .settings(
    name := "ChordialServer",
    assemblySettings,
    javaAgents += jettyAgent,
    libraryDependencies ++= loggingLibraryGroup ++ Seq(
      dependencies.betterFiles,
      dependencies.hasher
    )
  )
  .dependsOn(api)


lazy val assemblySettings = assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case n if n.startsWith("application.conf") => MergeStrategy.concat
  case n if n.startsWith("reference.conf") => MergeStrategy.concat
  case _ => MergeStrategy.first
}