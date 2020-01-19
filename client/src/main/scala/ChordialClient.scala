import org.slf4j.LoggerFactory
import schema.ImplicitGrpcConversions._
import schema.service.{GetRequest, PostRequest, ReadinessCheck}
import scopt.{OptionDef, OptionParser}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}


private object ChordialClient extends App {

  import ClientHandler._

  lazy val log = LoggerFactory.getLogger(ChordialClient.getClass)


  val parser: OptionParser[ClientHandler] = new OptionParser[ClientHandler]("chordialClient") {

    head(
      """This Chordial client program is a tool to interact with the database node instances
        |For more information, check out: https://github.com/Qwe1rty/Chordial
        |
        |""".stripMargin)

    private val optionMappings = Map[Char, OptionDef[_, ClientHandler]](

      'k' -> opt[String]('k', "key")
          .required()
          .action((keyParam, handler) => handler.copy(key = Some(keyParam)))
          .text("The key for an entry in the database"),

      'v' -> opt[String]('v', "value")
          .required()
          .action((valueParam, handler) => handler.copy(value = Some(valueParam)))
          .text("The value associated with a key"),

      't' -> opt[String]('t', "timeout")
          .validate(timeoutParam => {
            Try(Duration(timeoutParam)) match {
              case Success(_) => success
              case Failure(e) => failure(e.getMessage)
            }
          })
          .action((timeoutParam, handler) => handler.copy(timeout = Duration(timeoutParam)))
          .text("The timeout for the resulting gRPC call made to the server"),

      'h' -> opt[String]('h', "host")
          .action((hostParam, handler) => handler.copy(host = hostParam))
          .text("The hostname to target. If omitted, it will contact the address 0.0.0.0")
    )

    help("help")
      .text("prints this usage text")

    cmd("get")
      .children(
        optionMappings('k')
      )
      .action((_, handler) => {
        log.info("Sending GET request")

        Try(Await.ready(
          handler.get(GetRequest(handler.key.get)),
          handler.timeout * 2
        ))
        match {
          case Success(future) => future.value.get match {
            case Success(getResponse) =>
              val stringValue: String = getResponse.value // Convert ByteString to String
              log.info(s"GET request successful: ${stringValue}")
              handler
            case Failure(requestError) =>
              log.warn(s"GET request failed: ${requestError}")
              handler.copy(conclusion = Failure(requestError))
          }
          case Failure(timeout) =>
            log.error(s"Internal client timeout error during GET: ${timeout}")
            handler.copy(conclusion = Failure(timeout))
        }
      })
      .text("Get a value from the database")

    cmd("post")
      .children(
        optionMappings('k'),
        optionMappings('v')
      )
      .action((_, handler) => {
        log.info("Sending POST request")

        Try(Await.ready(
          handler.post(PostRequest(
            handler.key.get,
            handler.value.get
          )),
          handler.timeout * 2
        ))
        match {
          case Success(future) => future.value.get match {
            case Success(postResponse) =>
              log.info(s"POST request successful: ${postResponse}")
              handler
            case Failure(requestError) =>
              log.info(s"POST request failed: ${requestError}")
              handler.copy(conclusion = Failure(requestError))
          }
          case Failure(timeout) =>
            log.error(s"Internal client timeout error during POST: ${timeout}")
            handler.copy(conclusion = Failure(timeout))
        }
      })
      .text("Insert a value into the database. If present, will overwrite existing value for the specified key")

    cmd("delete")
      .children(
        optionMappings('k')
      )
      .action((_, handler) => {
        val message = "Delete is not currently implemented"
        log.info(message)
        handler.copy(conclusion = Failure(new UnsupportedOperationException(message)))
      })
      .text("Delete a value from the database")

    cmd("ready")
      .action((_, handler) => {
        Try(Await.ready(
          handler.readiness(ReadinessCheck()),
          handler.timeout * 2
        ))
        match {
          case Success(future) => future.value.get match {
            case Success(readinessResponse) =>
              log.info(s"Readiness response received with status: ${readinessResponse.isReady}")
              if (!readinessResponse.isReady) {
                handler.copy(conclusion = Failure(new IllegalMonitorStateException("Node is not ready")))
              }
              else handler

            case Failure(requestError) =>
              log.info(s"Readiness check failed: ${requestError}")
              handler.copy(conclusion = Failure(requestError))
          }
          case Failure(timeout) =>
            log.error(s"Internal client timeout error during readiness check: ${timeout}")
            handler.copy(conclusion = Failure(timeout))
        }
      })
      .text("Perform a readiness check - readiness indicates the node is ready to receive requests")
  }


  parser.parse(args, ClientHandler()) match {
    case Some(handler) =>
      handler.conclusion match {
        case Success(_) => sys.exit(0)
        case Failure(_) => sys.exit(1)
      }
    case None => sys.exit(1)
  }
}
