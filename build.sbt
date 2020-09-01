import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.javaAgents
import sbt.project

ThisBuild / version := "2.0.0"
ThisBuild / scalaVersion := "2.13.1"


/**
 * Dependency definitions
 */
lazy val dependencies =
  new {

    // Networking/RPC libraries
    val protoCommonV   = "1.17.0"
    val grpcCommonV    = "1.17.0"
    val scalaProtoV    = scalapb.compiler.Version.scalapbVersion
//    val scalaCompilerV = "0.10.0"
    val uuidV          = "0.3.1"
    val ipAddressesV   = "scala_upgrade"

    val protoCommon   = "com.google.api.grpc"   % "proto-google-common-protos" % protoCommonV % "protobuf"
    val grpcCommon    = "com.google.api.grpc"   % "grpc-google-common-protos"  % grpcCommonV  % "protobuf"
    val scalaProto    = "com.thesamet.scalapb" %% "scalapb-runtime"            % scalaProtoV  % "protobuf"
//    val scalaCompiler = "com.thesamet.scalapb" %% "compilerplugin"             % scalaCompilerV
    val uuid          = "io.jvm.uuid"          %% "scala-uuid"                 % uuidV

    // Akka libraries
    val akkaActorV   = "2.6.8"
    val akkaStreamV  = "2.6.8"

    val akkaActor   = "com.typesafe.akka"    %% "akka-actor-typed"  % akkaActorV
    val akkaStream  = "com.typesafe.akka"    %% "akka-stream-typed" % akkaStreamV

    // Logging libraries
    val akkaSLF4jV = "2.6.8"
    val logbackV   = "1.2.3"

    val akkaSLF4j = "com.typesafe.akka" %% "akka-slf4j"      % akkaSLF4jV
    val logback   = "ch.qos.logback"     % "logback-classic" % logbackV

    // Server-specific libraries
    val betterFilesV = "3.8.0"
    val hasherV      = "1.2.2"

    val betterFiles = "com.github.pathikrit" %% "better-files" % betterFilesV
    val hasher      = "com.outr"             %% "hasher"       % hasherV

    // Client-specific libraries
    val scoptV = "3.7.1"

    val scopt = "com.github.scopt" %% "scopt" % scoptV
  }

lazy val ipAddresses = /* Temporary solution for now */
  RootProject(uri(s"git://github.com/Qwe1rty/ipaddr.git#${dependencies.ipAddressesV}"))

lazy val jettyAgent = "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test"

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value / "scalapb"
)


lazy val grpcLibraryGroup = Seq(
  dependencies.protoCommon,
  dependencies.grpcCommon,
//  dependencies.scalaCompiler,
  dependencies.scalaProto,
  dependencies.uuid,
)

lazy val akkaLibraryGroup = Seq(
  dependencies.akkaActor,
  dependencies.akkaStream,
)

lazy val loggingLibraryGroup = Seq(
  dependencies.akkaSLF4j,
  dependencies.logback
)


/**
 * Project definitions
 */
lazy val root = (project in file("."))
  .aggregate(client, server)
  .disablePlugins(AssemblyPlugin)

lazy val api = (project in file("api"))
  .dependsOn(ipAddresses)
  .enablePlugins(
    AkkaGrpcPlugin,
    JavaAgent /*ALPN agent*/
  )
  .settings(
    name := "ChordialSchema",
    javaAgents += jettyAgent,
    libraryDependencies ++= akkaLibraryGroup ++ grpcLibraryGroup
  )
  .disablePlugins(AssemblyPlugin)


lazy val client = (project in file("client"))
  .dependsOn(ipAddresses)
  .dependsOn(api)
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

lazy val server = (project in file("server"))
  .dependsOn(ipAddresses)
  .dependsOn(api)
  .enablePlugins(
    AkkaGrpcPlugin,
    JavaAppPackaging,
    JavaAgent /*ALPN agent*/
  )
  .settings(
    name := "ChordialServer",
    assemblySettings,
    javaAgents += jettyAgent,
    libraryDependencies ++= akkaLibraryGroup ++ loggingLibraryGroup ++ Seq(
      dependencies.betterFiles,
      dependencies.hasher,
      dependencies.uuid,
    )
  )


/**
 * SBT Assembly plugin configuration
 */
lazy val assemblySettings = assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case n if n.startsWith("application.conf") => MergeStrategy.concat
  case n if n.startsWith("reference.conf") => MergeStrategy.concat
  case _ => MergeStrategy.first
}