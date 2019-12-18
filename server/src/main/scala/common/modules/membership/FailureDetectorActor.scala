package common.modules.membership

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import akka.pattern.ask
import akka.stream.ActorMaterializer
import com.risksense.ipaddr.IpAddress
import common.utils.ActorTimers.Tick
import common.utils.{ActorDefaults, ActorTimers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}


object FailureDetectorActor {

  private case class DirectResponse(target: Membership, directResult: Try[Confirmation])

  private case class FollowupRequest(target: Membership)
  private case class FollowupResponse(target: Membership, followupResult: Try[Confirmation])

  private val CONNECTIONS_DEADLINE: Duration = 20.second

  private val DIRECT_CONNECTIONS_LIMIT: Int = 5
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

  private var scheduledDirectChecks: Int = 0 // Counting variable, acts as publish "semaphore"

  private var pendingDirectChecks: Set[Membership] = Set[Membership]()
  private var pendingFollowupChecks: Map[Membership, Int] = Map[Membership, Int]()

  import FailureDetectorActor._
  import MembershipAPI._
  import common.ChordialDefaults.INTERNAL_REQUEST_TIMEOUT

  start(1500.millisecond)


  implicit def grpcSettingsFactory(ipAddress: IpAddress): GrpcClientSettings =
    GrpcClientSettings
      .connectToServiceAt(
        ipAddress.toString,
        common.ChordialDefaults.FAILURE_DETECTOR_PORT
      )
      .withDeadline(CONNECTIONS_DEADLINE)

  override def receive: Receive = {

    case Tick => if (scheduledDirectChecks < DIRECT_CONNECTIONS_LIMIT) {

      scheduledDirectChecks = scheduledDirectChecks + 1

      (membershipActor ? GetRandomNode())
        .mapTo[Option[Membership]]
        .onComplete {

        case Success(requestResult) => requestResult.foreach { target =>

          val grpcClient = FailureDetectorServiceClient(target.ipAddress)
          pendingDirectChecks = pendingDirectChecks + target

          log.debug(s"Attempting to check failure for node ${target}")
          grpcClient.directCheck(DirectMessage()).onComplete {
            self ! DirectResponse(target, _)
          }
        }

        case Failure(e) => log.error(s"Error encountered on membership random node request: ${e}")
      }
    }

    case DirectResponse(target, directResult) => directResult match {

      case Success(_) =>
        scheduledDirectChecks = scheduledDirectChecks - 1
        pendingDirectChecks = pendingDirectChecks - target

      case Failure(_) =>
        membershipActor ! DeclareEvent(NodeState.SUSPECT, target)
        self ! FollowupRequest(target)
    }

    case FollowupRequest(target) => {

      (membershipActor ? GetRandomNodes(FOLLOWUP_TEAM_SIZE))
        .mapTo[Seq[Membership]]
        .onComplete {

        case Success(requestResult) =>

          log.debug(s"Attempting to followup on suspected dead node ${target}")
          requestResult.par.foreach { member =>

            val grpcClient = FailureDetectorServiceClient(member.ipAddress)
            // TODO update pendingFollowupChecks

            log.debug(s"Calling ${member} for indirect check on ${target}")
            grpcClient.followupCheck(FollowupMessage(target.ipAddress.key._1.toInt)).onComplete {
              self ! FollowupResponse(member, _)
            }
          }

        case Failure(e) => log.error(s"Error encountered on ${FOLLOWUP_TEAM_SIZE} random node request: ${e}")
      }
    }

    case FollowupResponse(suspect, followupResult) => followupResult match {
      case Success(_) => ???
      case Failure(_) => ???
    }

    case x => log.error(receivedUnknown(x))
  }
}
