package common.modules.failureDetection

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.ActorMaterializer
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

  def apply(membershipActor: ActorRef)(implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(
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


  // TODO possible version number tracing (from the membership actor) during failure detection cycle
  override def receive: Receive = {

    case Tick => if (scheduledDirectChecks < DIRECT_CONNECTIONS_LIMIT) {
      scheduledDirectChecks += 1

      (membershipActor ? GetRandomNode())
        .mapTo[Option[Membership]]
        .onComplete(self ! DirectRequest(_))
    }

    case DirectRequest(potentialTarget) => potentialTarget match {

      case Success(requestResult) => requestResult.foreach { target =>
        val grpcClient = FailureDetectorServiceClient(createGrpcSettings(target.ipAddress, SUSPICION_DEADLINE))
        pendingDirectChecks += target

        log.debug(s"Attempting to check failure for node ${target}")
        grpcClient.directCheck(DirectMessage()).onComplete {
          self ! DirectResponse(target, _)
        }
      }

      case Failure(e) => {
        log.error(s"Error encountered on membership random node request: ${e}")
        scheduledDirectChecks -= 1
      }
    }

    case DirectResponse(target, directResult) => directResult match {

      case Success(_) =>
        scheduledDirectChecks -= 1
        pendingDirectChecks -= target

      case Failure(_) =>
        self ! FollowupTrigger(target)
    }


    case FollowupTrigger(target) => {

      (membershipActor ? GetRandomNodes(FOLLOWUP_TEAM_SIZE))
        .mapTo[Seq[Membership]]
        .onComplete(self ! FollowupRequest(target, _))
    }

    case FollowupRequest(target, followupTeam) => followupTeam match {

      case Success(requestResult) =>
        log.debug(s"Attempting to followup on suspected dead node ${target}")
        requestResult.foreach { member =>

          val grpcClient = FailureDetectorServiceClient(createGrpcSettings(member.ipAddress, DEATH_DEADLINE))
          pendingFollowupChecks = pendingFollowupChecks + (member -> requestResult.size)

          log.debug(s"Calling ${member} for indirect check on ${target}")
          grpcClient.followupCheck(FollowupMessage(target.ipAddress)).onComplete {
            self ! FollowupResponse(member, _)
          }
        }

      case Failure(e) => log.error(s"Error encountered on ${FOLLOWUP_TEAM_SIZE} random node request: ${e}")
    }

    case FollowupResponse(target, followupResult) => followupResult match {

      case Success(_) =>
        pendingFollowupChecks -= target
        log.debug(s"Followup on target ${target} successful, removing suspicion status")

      case Failure(_) =>
        pendingFollowupChecks += target -> (pendingFollowupChecks(target) - 1)
        log.debug(s"Followup failure on target ${target}, ")

        if (pendingFollowupChecks(target) <= 0) {
          pendingFollowupChecks -= target
          membershipActor ! DeclareEvent(NodeState.SUSPECT, target)
          log.info(s"Target ${target} seen as suspect, verifying with membership service")

          actorSystem.scheduler.scheduleOnce(DEATH_DEADLINE)(self ! DeclareDeath(target))
        }
    }


    case DeclareDeath(target) => {
      membershipActor ! DeclareEvent(NodeState.DEAD, target)
      log.info(s"Death timer run out for ${target}, verifying with membership service for possible declaration")
    }

    case x => log.error(receivedUnknown(x))
  }
}
