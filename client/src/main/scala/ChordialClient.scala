import ch.qos.logback.classic.{Level, Logger}
import org.slf4j.LoggerFactory
import schema.ImplicitGrpcConversions._
import schema.service.{GetRequest, PostRequest, ReadinessCheck}
import scopt.OptionParser

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}


/**
 * This is the CLI client tool for the Chordial database. It provides an interface to conveniently
 * call the external server gRPC methods
 *
 * For the specification of this stub, run the client stub with the --help argument
 */
private object ChordialClient extends App {

  import ClientHandler._

  lazy val separator = sys.props("line.separator")

  // Needed as the the netty I/O logs on DEBUG mode are excessive
  LoggerFactory
    .getLogger("io.grpc.netty")
    .asInstanceOf[Logger]
    .setLevel(Level.INFO)


  val parser: OptionParser[ClientHandler] = new OptionParser[ClientHandler]("chordial") {

    head(
      """This Chordial client program is a CLI tool to interact with the database node instances
        |For more information, check out: https://github.com/Qwe1rty/Chordial
        |""".stripMargin)


    opt[String]('k', "key")
      .action((keyParam, handler) => handler.copy(key = Some(keyParam)))
      .text("key for an entry in the database")

    opt[String]('v', "value")
      .action((valueParam, handler) => handler.copy(value = Some(valueParam)))
      .text("value associated with a key")

    opt[String]('t', "timeout")
      .validate(timeoutParam => {
        Try(Duration(timeoutParam)) match {
          case Success(_) => success
          case Failure(e) => failure(e.getMessage)
        }
      })
      .action((timeoutParam, handler) => handler.copy(timeout = Duration(timeoutParam)))
      .text("timeout for the resulting gRPC call made to the server. If omitted, it will be set to 10 seconds")

    opt[String]('h', "host")
      .action((hostParam, handler) => handler.copy(host = hostParam))
      .text("hostname to target. If omitted, the address 0.0.0.0 will be used")


    help("help")
      .text("prints this usage text")

    note(separator)


    cmd("get")
      .action((_, handler) => handler.copy(operation = GET))
      .text("Get a value from the database" + separator)

    cmd("post")
      .action((_, handler) => handler.copy(operation = POST))
      .text("Insert a value into the database. If present, will overwrite existing value for the specified key" + separator)

    cmd("delete")
      .action((_, handler) => handler.copy(operation = DELETE))
      .text("Delete a value from the database" + separator)

    cmd("ready")
      .action((_, handler) => handler.copy(operation = READY))
      .text("Perform a readiness check - readiness indicates the node is ready to receive requests" + separator)


    checkConfig(handler => {
      if (handler.operation.equals(POST) && handler.value.isEmpty) {
        failure("Value field cannot be empty")
      }
      else handler.operation match {
        case _ @ (GET | POST | DELETE) =>  {
          if (handler.key.isEmpty) failure("Key field cannot be empty")
          else success
        }
        case _ => success
      }
    })

  }


  // The exit code specifications (especially the error exit codes) are especially needed, as it's used
  // to signal to the Kubernetes liveness/readiness probes whether or not the result was successful
  //
  parser.parse(args, ClientHandler()) match {
    case Some(handler) => handler.operation match {

      case GET =>
        Try(Await.ready(
          handler.get(GetRequest(handler.key.get)),
          handler.timeout * 2
        ))
        match {
          case Success(future) => future.value.get match {
            case Success(getResponse) =>
              val stringValue: String = getResponse.value // Convert ByteString to String
              println(s"GET request successful: ${stringValue}")
              sys.exit(0)
            case Failure(requestError) =>
              println(s"GET request failed: ${requestError}")
              sys.exit(3)
          }
          case Failure(timeout) =>
            println(s"Internal client error during GET: ${timeout}")
            sys.exit(2)
        }

      case POST =>
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
              println(s"POST request successful: ${postResponse}")
              sys.exit(0)
            case Failure(requestError) =>
              println(s"POST request failed: ${requestError}")
              sys.exit(4)
          }
          case Failure(timeout) =>
            println(s"Internal client error during POST: ${timeout}")
            sys.exit(3)
        }

      case DELETE =>
        println("Deletes are currently not implemented")
        sys.exit(2)

      case READY =>
        Try(Await.ready(
          handler.readiness(ReadinessCheck()),
          handler.timeout * 2
        ))
        match {
          case Success(future) => future.value.get match {
            case Success(readinessResponse) =>
              println(s"Readiness response received with status: ${readinessResponse.isReady}")
              if (!readinessResponse.isReady) {
                println("Node is not ready - reporting exit code as failure")
                sys.exit(5)
              }
              else sys.exit(0)

            case Failure(requestError) =>
              println(s"Readiness check failed: ${requestError}")
              sys.exit(4)
          }
          case Failure(timeout) =>
            println(s"Internal client error during readiness check: ${timeout}")
            sys.exit(3)
        }

      case _ =>
        println("Command not specified, please use the --help flag for more info")
        sys.exit(100)
    }

    case None => sys.exit(1)
  }

}
