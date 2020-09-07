import administration.Administration.DeclareEvent
import administration.addresser.KubernetesAddresser
import administration.{Administration, Membership}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
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
  implicit val actorSystem: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.setup[Nothing] { context =>

      /**
       * Administration components
       *   TODO: eventually allow different address retriever methods
       */
      log.info("Initializing administration components")
      val addressRetriever = KubernetesAddresser
      val administrationComponent = context.spawn(
        Administration(addressRetriever, REQUIRED_TRIGGERS),
        "administration"
      )
      log.info("Administration components initialized")

      /**
       * Persistence layer components
       */
      log.info("Initializing top-level persistence layer components")
      val persistenceComponent = context.spawn(
        PersistenceComponent(administrationComponent),
        "persistence"
      )
      log.info("Persistence layer components created")

      /**
       * Replication layer components
       */
      log.info("Initializing raft and replication layer components")
      val replicationComponent = context.spawn(
        ReplicationComponent(administrationComponent, persistenceComponent, addressRetriever),
        "replication"
      )
      log.info("Replication layer components created")

      /**
       * Service layer components
       */
      log.info("Initializing external facing gRPC service")
      ClientGRPCService(replicationComponent, administrationComponent)(context.system)
      log.info("Service layer components created")


      scala.sys.addShutdownHook(administrationComponent ! DeclareEvent(
        NodeState.DEAD,
        Membership(Administration.nodeID, addressRetriever.selfIP)
      ))

      Behaviors.empty
    },
    "ChordialServer",
    config
  )

}
