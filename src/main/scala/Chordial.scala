import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import persistence.io.PersistenceActor
import persistence.threading.ThreadPartitionActor
import server.service.RequestServiceInitializer


object Chordial extends App {

  val config = ConfigFactory
    .parseString("akka.http.server.preview.enable-http2 = on")
    .withFallback(ConfigFactory.defaultApplication())

  implicit val actorSystem: ActorSystem = ActorSystem("Chordial", config)

  // Persistence layer top-level actors
  val threadPartitionActor = actorSystem.actorOf(ThreadPartitionActor.props)
  val persistenceActor = actorSystem.actorOf(PersistenceActor.props(threadPartitionActor))

  RequestServiceInitializer(persistenceActor).run()
}