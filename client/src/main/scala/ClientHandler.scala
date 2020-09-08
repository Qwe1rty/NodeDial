import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import schema.PortConfiguration.EXTERNAL_REQUEST_PORT
import schema.service.RequestServiceClient

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.Duration
import scala.language.implicitConversions


object ClientHandler {

  val config: Config = ConfigFactory.load()

  implicit val actorSystem: ActorSystem = ActorSystem("NodeDialClient", config)
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher

  implicit def handlerToStub(params: ClientHandler): RequestServiceClient = {

    RequestServiceClient(GrpcClientSettings
      .connectToServiceAt(
        params.host,
        EXTERNAL_REQUEST_PORT
      )
      .withDeadline(params.timeout)
    )
  }

}


case class ClientHandler(
  key:        Option[String] = None,
  value:      Option[String] = None,
  timeout:    Duration       = Duration(10, TimeUnit.SECONDS),
  host:       String         = "0.0.0.0",

  operation: ClientOperation = UNSPECIFIED
)
