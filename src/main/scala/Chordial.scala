import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import persistence.PersistenceActor
import server.service.RequestServiceInitializer

object Chordial extends App {

  val config = ConfigFactory
    .parseString("akka.http.server.preview.enable-http2 = on")
    .withFallback(ConfigFactory.defaultApplication())

  implicit val actorSystem: ActorSystem = ActorSystem("Chordial", config)
  val persistenceActor = actorSystem.actorOf(Props[PersistenceActor])

  RequestServiceInitializer(persistenceActor).run()
}