package common.modules.membership

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import akka.pattern.ask
import akka.stream.ActorMaterializer
import common.utils.ActorTimers.Tick
import common.utils.{ActorDefaults, ActorTimers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}


object FailureDetectorActor {

  private case class Followup(membership: MembershipPair)

  private val FOLLOWUP_TEAM_SIZE: Int = 3 // TODO move this somewhere better


  def apply(membershipActor: ActorRef)(implicit actorContext: ActorContext, actorSystem: ActorSystem): ActorRef =
    actorContext.actorOf(
      Props(new FailureDetectorActor(membershipActor)),
      "failureDetectorActor"
    )
}


class FailureDetectorActor
    (membershipActor: ActorRef)
    (implicit actorSystem: ActorSystem)
  extends Actor
  with ActorLogging
  with ActorDefaults
  with ActorTimers {

  implicit private val materializer: ActorMaterializer = ActorMaterializer()(context)
  implicit private val executionContext: ExecutionContext = actorSystem.dispatcher

  private var pendingConnections: Int = 0 // TODO replace this with a set to prevent duplicates

  import FailureDetectorActor._
  import MembershipAPI._
  import common.ChordialDefaults.INTERNAL_REQUEST_TIMEOUT

  start(2.second)


  override def receive: Receive = {

    case Tick => (membershipActor ? GetRandomNode()).mapTo[Option[MembershipPair]].onComplete {

      case Success(requestResult) => requestResult.foreach { membership =>

        // TODO impose pendingConnections limit

        log.debug(s"Attempting to check failure for node ${}")
        val grpcSettings = GrpcClientSettings.connectToServiceAt(
          membership.ipAddress.toString,
          common.ChordialDefaults.FAILURE_DETECTOR_PORT
        )
        val grpcClient = FailureDetectorServiceClient(grpcSettings)

        pendingConnections = pendingConnections + 1
        grpcClient.failureCheck(Check()).onComplete {
          case Success(_) => pendingConnections = pendingConnections - 1
          case Failure(_) =>
            membershipActor ! ReportEvent(NodeState.SUSPECT, membership)
            self ! Followup(membership)
        }
      }

      case Failure(e) => log.error(s"Error encountered on membership random node request: ${e}")
    }

    case Followup(membership) => (membershipActor ? GetRandomNodes(FOLLOWUP_TEAM_SIZE)).mapTo[MembershipPair].onComplete {
      ???
    }

    case x => log.error(receivedUnknown(x))
  }
}
