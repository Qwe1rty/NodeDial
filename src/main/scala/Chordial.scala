import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import persistence.io.PersistenceActor
import persistence.threading.ThreadPartitionActor
import server.service.RequestServiceInitializer


object Chordial extends App {

  val log = LoggerFactory.getLogger(Chordial.getClass)

  val config = ConfigFactory
    .parseString("akka.http.server.preview.enable-http2 = on")
    .withFallback(ConfigFactory.defaultApplication())
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