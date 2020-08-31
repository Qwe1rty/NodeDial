package replication

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util
import akka.util.Timeout
import common.persistence.Serializer
import common.time.TimeRange
import io.jvm.uuid._
import membership.MembershipActor
import membership.addresser.AddressRetriever
import membership.api.Membership
import replication.Raft.CommitFunction
import replication.eventlog.SimpleReplicatedLog
import replication.state.RaftState

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration


/**
 * The external API for Raft.
 *
 * @param addresser      the address retriever so Raft can check its own IP
 * @param commitCallback the function that the Raft process calls after it has determined that a
 *                       majority of the cluster has agreed to append the log entry, and now needs to be
 *                       applied to the state machine as dictated by user code
 * @param actorSystem    the actor system
 * @tparam Command the command type to be applied to the state machine
 */
class Raft[Command <: Serializable](addresser: AddressRetriever, commitCallback: CommitFunction[Command])(implicit actorSystem: ActorSystem) {

  /**
   * The serializer is used to convert the log entry bytes to the command object, for when
   * Raft determines an entry needs to be committed
   */
  this: Serializer[Command] =>

  private val raft = actorSystem.actorOf(
    Props(new RaftFSM[Command](
      RaftState(
        Membership(MembershipActor.nodeID, addresser.selfIP),
        new SimpleReplicatedLog(ReplicationComponent.REPLICATED_LOG_INDEX, ReplicationComponent.REPLICATED_LOG_DATA)
      ),
      commitCallback,
      this
    )),
    s"raftActor-${UUID.random}"
  )


  def submit(appendEntryEvent: AppendEntryEvent): Future[AppendEntryAck] = {
    implicit def timeout: util.Timeout = Timeout(Raft.NEW_LOG_ENTRY_TIMEOUT)

    (raft ? appendEntryEvent)
      .mapTo[Future[AppendEntryAck]]
      .flatten
  }
}

object Raft {

  /**
   * The commit function is called after the Raft process has determined a majority of the
   * servers have agreed to append the log entry, and now needs to be applied to the state
   * machine as dictated by user code
   */
  type CommitConfirmation = Unit
  type CommitFunction[Command] = Function[Command, Future[CommitConfirmation]]

  val ELECTION_TIMEOUT_LOWER_BOUND: FiniteDuration = FiniteDuration(150, TimeUnit.MILLISECONDS)
  val ELECTION_TIMEOUT_UPPER_BOUND: FiniteDuration = FiniteDuration(325, TimeUnit.MILLISECONDS)

  val ELECTION_TIMEOUT_RANGE: TimeRange = TimeRange(ELECTION_TIMEOUT_LOWER_BOUND, ELECTION_TIMEOUT_UPPER_BOUND)
  val INDIVIDUAL_NODE_TIMEOUT: FiniteDuration = FiniteDuration(50, TimeUnit.MILLISECONDS)
  val NEW_LOG_ENTRY_TIMEOUT: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)
}
