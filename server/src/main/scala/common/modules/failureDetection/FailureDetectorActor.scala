package common.modules.failureDetection

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import akka.pattern.ask
import akka.stream.ActorMaterializer
import com.risksense.ipaddr.IpAddress
import common.ChordialDefaults.ACTOR_REQUEST_TIMEOUT
import common.modules.failureDetection.FailureDetectorConstants._
import common.modules.failureDetection.FailureDetectorSignal._
import common.modules.membership.MembershipAPI._
import common.modules.membership._
import common.utils.ActorTimers.Tick
import common.utils.{ActorDefaults, ActorTimers}
import schema.ImplicitDataConversions._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.{Failure, Success}


object FailureDetectorActor {

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

  start(1500.millisecond)


  implicit def grpcSettingsFactory(ipAddress: IpAddress): GrpcClientSettings =
    GrpcClientSettings
      .connectToServiceAt(
        ipAddress.toString,
        common.ChordialDefaults.FAILURE_DETECTOR_PORT
      )
      .withDeadline(SUSPICION_DEADLINE)

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
            pendingFollowupChecks = pendingFollowupChecks + (member -> requestResult.size)

            log.debug(s"Calling ${member} for indirect check on ${target}")
            grpcClient.followupCheck(FollowupMessage(target.ipAddress)).onComplete {
              self ! FollowupResponse(member, _)
            }
          }

        case Failure(e) => log.error(s"Error encountered on ${FOLLOWUP_TEAM_SIZE} random node request: ${e}")
      }
    }

    case FollowupResponse(target, followupResult) => followupResult match {

      case Success(_) =>
        pendingFollowupChecks = pendingFollowupChecks - target

      case Failure(_) =>
        pendingFollowupChecks = pendingFollowupChecks.updated(target, pendingFollowupChecks(target) - 1)

        if (pendingFollowupChecks(target) <= 0) {
          pendingFollowupChecks = pendingFollowupChecks - target
          membershipActor ! DeclareEvent(NodeState.SUSPECT, target)

          // TODO declare DEAD after some period of time
        }
    }

    case DeclareDeath(target) => ???

    case AbsolveDeath(target) => ???

    case x => log.error(receivedUnknown(x))
  }
}
