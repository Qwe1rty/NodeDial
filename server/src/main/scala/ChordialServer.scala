import akka.actor.ActorSystem
import ch.qos.logback.classic.Level
import com.typesafe.config.ConfigFactory
import common.ServerConstants._
import common.membership.types.NodeState
import membership.addresser.KubernetesAddresser
import membership.api.DeclareEvent
import membership.failureDetection.{FailureDetectorActor, FailureDetectorGRPCService}
import membership.{Administration, Membership, MembershipGRPCService}
import org.slf4j.LoggerFactory
import persistence.PersistenceActor
import persistence.execution.PartitionedTaskExecutor
import replication.{RaftGRPCService, ReplicationComponent}
import schema.LoggingConfiguration
import service.{RequestServiceImpl, ServiceActor}


private object ChordialServer extends App {

  val config = ConfigFactory.load()

  LoggingConfiguration.setPackageLevel(Level.INFO,
    "io.grpc.netty",
    "akka.http.impl.engine.http2",
    "akka.io",
    "akka.actor"
  )

  val log = LoggerFactory.getLogger(ChordialServer.getClass)
  log.info("Server config loaded")

  log.info("Initializing actor system")
  implicit val actorSystem: ActorSystem = ActorSystem("ChordialServer", config)


  /**
   * Membership module components
   *   TODO: eventually allow different address retriever methods
   */
  log.info("Initializing membership module components")

  val addressRetriever = KubernetesAddresser

  val membershipActor = Administration(addressRetriever, REQUIRED_TRIGGERS)
  MembershipServiceImpl(membershipActor)

  val failureDetectorActor = FailureDetectorActor(membershipActor)
  FailureDetectorGRPCService()

  log.info("Membership module components initialized")


  /**
   * Persistence layer components
   */
  log.info("Initializing top-level persistence layer components")

  val threadPartitionActor = PartitionedTaskExecutor()
  val persistenceActor = PersistenceActor(threadPartitionActor, membershipActor)

  log.info("Persistence layer components created")


  /**
   * Replication layer components
   */
  log.info("Initializing raft and replication layer components")

  val replicationActor = ReplicationActor(addressRetriever, persistenceActor)

  log.info("Replication layer components created")


  /**
   * Service layer components
   */
  log.info("Initializing external facing gRPC service")

  val requestServiceActor = ServiceActor(persistenceActor, membershipActor)
  RequestServiceImpl(requestServiceActor, membershipActor)

  log.info("Service layer components created")


  scala.sys.addShutdownHook(membershipActor ! DeclareEvent(
    NodeState.DEAD,
    Membership(Administration.nodeID, addressRetriever.selfIP)
  ))
}
