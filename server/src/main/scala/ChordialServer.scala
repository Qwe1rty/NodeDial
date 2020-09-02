import administration.Administration.DeclareEvent
import administration.addresser.KubernetesAddresser
import administration.{Administration, Membership}
import akka.actor.typed.scaladsl.{AbstractBehavior, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior}
import ch.qos.logback.classic.Level
import com.typesafe.config.ConfigFactory
import common.ServerConstants._
import common.administration.types.NodeState
import org.slf4j.LoggerFactory
import persistence.PersistenceComponent
import replication.ReplicationComponent
import schema.LoggingConfiguration


private object ChordialServer extends App {

  val config = ConfigFactory.load()

  LoggingConfiguration.setPackageLevel(Level.INFO,
    "io.grpc.netty",
    "akka.http.impl.engine.http2",
    "akka.io",
    "akka.actor"
  )

  val log = LoggerFactory.getLogger(ChordialServer.getClass)
  log.info("Server config loaded")

  log.info("Initializing actor system")
  implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.setup { context =>
    new AbstractBehavior[Nothing](context) { override def onMessage(msg: Nothing): Behavior[Nothing] = {

      /**
       * Membership module components
       *   TODO: eventually allow different address retriever methods
       */
      log.info("Initializing membership module components")
      val addressRetriever = KubernetesAddresser
      val membershipComponent = context.spawn(
        Administration(addressRetriever, REQUIRED_TRIGGERS),
        "administration"
      )
      log.info("Membership module components initialized")

      /**
       * Persistence layer components
       */
      log.info("Initializing top-level persistence layer components")
      val persistenceComponent = context.spawn(
        PersistenceComponent(membershipComponent),
        "persistence"
      )
      log.info("Persistence layer components created")

      /**
       * Replication layer components
       */
      log.info("Initializing raft and replication layer components")
      val replicationComponent = context.spawn(
        ReplicationComponent(membershipComponent, persistenceComponent, addressRetriever),
        "replication"
      )
      log.info("Replication layer components created")

      /**
       * Service layer components
       */
      log.info("Initializing external facing gRPC service")
      ClientGRPCService(replicationComponent, membershipComponent)(context.system)
      log.info("Service layer components created")


      scala.sys.addShutdownHook(membershipComponent ! DeclareEvent(
        NodeState.DEAD,
        Membership(Administration.nodeID, addressRetriever.selfIP)
      ))

      this
    }}
  }, "ChordialServer", config)

}
