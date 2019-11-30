import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import persistence.io.PersistenceActor
import persistence.threading.ThreadPartitionActor
import server.service.RequestServiceInitializer


private object Chordial extends App {

  val config = ConfigFactory.load()

  val log = LoggerFactory.getLogger(Chordial.getClass)
  log.info("Akka gRPC config loaded")

  implicit val actorSystem: ActorSystem = ActorSystem("Chordial", config)

  // Persistence layer top-level actors
  val threadPartitionActor = ThreadPartitionActor()
  val persistenceActor = PersistenceActor(threadPartitionActor)
  log.info("Persistence layer top-level actors created")

  log.debug("Initializing gRPC service")
  RequestServiceInitializer(persistenceActor).run()
  log.info("gRPC service initialized")
}