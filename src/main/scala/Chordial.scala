import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import server.RequestServiceManager

object Chordial extends App {

  val config = ConfigFactory
    .parseString("akka.http.server.preview.enable-http2 = on")
    .withFallback(ConfigFactory.defaultApplication())
  implicit val actorSystem: ActorSystem = ActorSystem("Chordial", config)

  val requestService = RequestServiceManager
}