package membership.failureDetection

import akka.actor
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import common.ServerDefaults
import common.membership.failureDetection.{Confirmation, DirectMessage, FailureDetectorServiceClient, FollowupMessage}
import common.membership.types.NodeState
import membership.Administration.{AdministrationAPI, DeclareEvent, GetRandomNode, GetRandomNodes}
import membership.failureDetection.FailureDetector._
import membership.failureDetection.FailureDetectorConstants._
import membership.{Administration, Membership}
import schema.ImplicitDataConversions._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}


class FailureDetector private(
    private val context: ActorContext[FailureDetectorSignal],
    private val timer: TimerScheduler[FailureDetectorSignal],
    administration: ActorRef[AdministrationAPI]
  )
  extends AbstractBehavior[FailureDetectorSignal](context) {

  implicit private val classicSystem: actor.ActorSystem = context.system.classicSystem
  implicit private val executionContext: ExecutionContext = context.system.executionContext
  implicit private val administrationTimeout: Timeout = ServerDefaults.ACTOR_REQUEST_TIMEOUT

  private var scheduledDirectChecks: Int = 0 // Counting variable, acts as publish "semaphore"
  private var pendingDirectChecks: Set[Membership] = Set[Membership]()
  private var pendingFollowupChecks: Map[Membership, Int] = Map[Membership, Int]()

  // Periodically initiate the failure detection cycle on a random node by triggering a direct check
  timer.startTimerAtFixedRate(DirectCheckTrigger, 1500.millisecond)


  override def onMessage(signal: FailureDetectorSignal): Behavior[FailureDetectorSignal] = {
    signal match {

      case DirectCheckTrigger => if (scheduledDirectChecks <= DIRECT_CONNECTIONS_LIMIT) {
        scheduledDirectChecks += 1
        context.ask(administration, GetRandomNode(NodeState.ALIVE, _: ActorRef[Option[Membership]])) {
          case Success(node) => DirectRequest(node)
          case Failure(e) =>
            context.log.error(s"Error encountered on membership random node request: ${e}")
            scheduledDirectChecks -= 1
            DirectRequest(None)
        }
      }

      case DirectRequest(potentialTarget) => potentialTarget.foreach { target =>

        // Make the check if there's not one pending already and it's not calling itself
        if (!pendingDirectChecks.contains(target) && target.nodeID != Administration.nodeID) {
          pendingDirectChecks += target
          FailureDetectorServiceClient(createGRPCSettings(target.ipAddress, SUSPICION_DEADLINE))
            .directCheck(DirectMessage())
            .onComplete(context.self ! DirectResponse(target, _))
        }
        else scheduledDirectChecks -= 1
      }

      case DirectResponse(target, directResult) => directResult match {
        case Failure(_) => context.self ! FollowupCheckTrigger(target) // Perform indirect followup checks to confirm node death
        case Success(_) =>
          context.log.debug(s"Target ${target} successfully passed initial direct failure check")
          scheduledDirectChecks -= 1
          pendingDirectChecks -= target
      }

      case FollowupCheckTrigger(target) =>
        context.ask(administration, GetRandomNodes(NodeState.ALIVE, FOLLOWUP_TEAM_SIZE, _: ActorRef[Set[Membership]])) {
          case Success(followupTeam) => FollowupRequest(target, followupTeam)
          case Failure(e) =>
            context.log.error(s"Error encountered on ${FOLLOWUP_TEAM_SIZE} random node request: ${e}")
            FollowupRequest(target, Set())
        }

      case FollowupRequest(target, followupTeam) =>
        context.log.debug(s"Attempting to followup on suspected dead node ${target} with team size ${followupTeam.size}")
        pendingFollowupChecks += target -> followupTeam.size

        for (member <- followupTeam) {
          if (member.nodeID != Administration.nodeID) {
            context.log.debug(s"Calling ${member} for indirect check on ${target}")
            FailureDetectorServiceClient(createGRPCSettings(member.ipAddress, DEATH_DEADLINE))
              .followupCheck(FollowupMessage(target.ipAddress))
              .onComplete(context.self ! FollowupResponse(member, _))
          }
          else {
            // TODO remove this condition when MembershipTable can be selective on non-self
            context.log.debug(s"Self detected in followup team for ${target}")
            pendingFollowupChecks += target -> (pendingFollowupChecks(target) - 1)
          }
        }

      case FollowupResponse(target, followupResult) => followupResult match {

        case Success(_) =>
          scheduledDirectChecks -= 1
          pendingDirectChecks -= target
          pendingFollowupChecks -= target
          context.log.debug(s"Followup on target ${target} successful, removing suspicion status")

        case Failure(_) =>
          pendingFollowupChecks += target -> (pendingFollowupChecks(target) - 1)
          context.log.debug(s"Followup failure on target ${target}, ")

          if (pendingFollowupChecks(target) <= 0) {
            scheduledDirectChecks -= 1
            pendingDirectChecks -= target
            pendingFollowupChecks -= target

            administration ! DeclareEvent(NodeState.SUSPECT, target)
            context.log.info(s"Target ${target} seen as suspect, verifying with membership service")
            context.system.scheduler.scheduleOnce(DEATH_DEADLINE, new Runnable {
              override def run(): Unit = context.self ! DeclareDeath(target)
            })
          }
      }

      case DeclareDeath(target) =>
        administration ! DeclareEvent(NodeState.DEAD, target)
        context.log.info(s"Death timer run out for ${target}, verifying with membership service for possible declaration")
    }
    this
  }

}

object FailureDetector {

  def apply(administration: ActorRef[AdministrationAPI]): Behavior[FailureDetectorSignal] =
    Behaviors.setup(context => {
      Behaviors.withTimers(timer => {
        new FailureDetector(context, timer, administration)
      })
    })

  /** Actor protocol */
  private sealed trait FailureDetectorSignal

  private final case object DirectCheckTrigger extends FailureDetectorSignal
  private final case class DirectRequest(potentialTarget: Option[Membership]) extends FailureDetectorSignal
  private final case class DirectResponse(target: Membership, directResult: Try[Confirmation]) extends FailureDetectorSignal

  private final case class FollowupCheckTrigger(target: Membership) extends FailureDetectorSignal
  private final case class FollowupRequest(target: Membership, followupTeam: Set[Membership]) extends FailureDetectorSignal
  private final case class FollowupResponse(target: Membership, followupResult: Try[Confirmation]) extends FailureDetectorSignal

  /**
   * Trigger the failure detector to notify the membership actor that the node is dead.
   * If the membership actor has already received an event stating that the node refuted
   * the suspicion, then this message is discarded
   *
   * @param target the target node
   */
  private final case class DeclareDeath(target: Membership) extends FailureDetectorSignal
}