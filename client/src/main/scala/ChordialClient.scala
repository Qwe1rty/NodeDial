import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import schema.service.RequestService

import scala.concurrent.ExecutionContextExecutor

private object ChordialClient extends App {

  val config = ConfigFactory.load()

  val log = LoggerFactory.getLogger(ChordialClient.getClass)
  log.info("Client config loaded")

  implicit val actorSystem: ActorSystem = ActorSystem("HelloWorldClient", config)
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher

  val clientSettings = GrpcClientSettings.fromConfig(RequestService.name)
}
