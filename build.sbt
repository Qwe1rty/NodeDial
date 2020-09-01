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
    val protoBuildV  = "0.10.8"
    val scalaGrpcV   = scalapb.compiler.Version.scalapbVersion

    val commonProtosUnpackV = "1.17.0-0"
    val commonProtosV       = "1.17.0-0"

    val scalaProtoV  = scalapb.compiler.Version.scalapbVersion
    val nettyGrpcV   = scalapb.compiler.Version.grpcJavaVersion
    val uuidV        = "0.3.1"
    val ipAddressesV = "scala_upgrade"

    val protoBuild  = "com.thesamet.scalapb" %% "compilerplugin"       % protoBuildV
    val scalaGrpc   = "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalaGrpcV

    val commonProtosUnpack = "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.10" % commonProtosUnpackV % "protobuf"
    val commonProtos       = "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.10" % commonProtosV

    val scalaProto  = "com.thesamet.scalapb" %% "scalapb-runtime"      % scalaProtoV  % "protobuf"
    val nettyGrpc   = "io.grpc"               % "grpc-netty"           % nettyGrpcV
    val uuid        = "io.jvm.uuid"          %% "scala-uuid"           % uuidV

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


lazy val grpcLibraryGroup = Seq(
  dependencies.protoBuild,
  dependencies.scalaGrpc,

  dependencies.commonProtosUnpack,
  dependencies.commonProtos,

  dependencies.scalaProto,
  dependencies.nettyGrpc,
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
    ProtocPlugin,
    JavaAgent /*ALPN agent*/
  )
  .settings(
    name := "ChordialSchema",
    javaAgents += jettyAgent,
    PB.targets in Compile := Seq(scalapb.gen() -> (sourceManaged in Compile).value / "scalapb"),
    libraryDependencies ++= grpcLibraryGroup ++ akkaLibraryGroup
  )
  .disablePlugins(AssemblyPlugin)


lazy val client = (project in file("client"))
  .dependsOn(ipAddresses)
  .dependsOn(api)
  .enablePlugins(
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
    ProtocPlugin,
    JavaAppPackaging,
    JavaAgent /*ALPN agent*/
  )
  .settings(
    name := "ChordialServer",
    assemblySettings,
    javaAgents += jettyAgent,
    PB.targets in Compile := Seq(scalapb.gen() -> (sourceManaged in Compile).value / "scalapb"),
    libraryDependencies ++= grpcLibraryGroup ++ akkaLibraryGroup ++ loggingLibraryGroup ++ Seq(
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