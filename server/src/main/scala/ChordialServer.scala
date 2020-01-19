import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import common.ServerConstants._
import common.membership.types.NodeState
import membership.addresser.KubernetesAddresser
import membership.failureDetection.{FailureDetectorActor, FailureDetectorServiceImpl}
import membership.{Membership, MembershipAPI, MembershipActor}
import org.slf4j.LoggerFactory
import persistence.PersistenceActor
import persistence.threading.ThreadPartitionActor
import service.{RequestServiceActor, RequestServiceImpl}


private object ChordialServer extends App {

  val config = ConfigFactory.load()

  val log = LoggerFactory.getLogger(ChordialServer.getClass)
  log.info("Server config loaded")

  log.info("Initializing actor system")
  implicit val actorSystem: ActorSystem = ActorSystem("Chordial", config)


  // Membership module components
  //    TODO eventually allow different address retriever methods
  //
  log.info("Initializing membership module components")

  val addressRetriever = KubernetesAddresser
  val membershipActor = MembershipActor(addressRetriever, REQUIRED_TRIGGERS)

  val failureDetectorActor = FailureDetectorActor(membershipActor)
  FailureDetectorServiceImpl()

  log.info("Membership module components initialized")


  // Persistence layer components
  //
  log.info("Initializing top-level persistence layer components")

  val threadPartitionActor = ThreadPartitionActor()
  val persistenceActor = PersistenceActor(threadPartitionActor, membershipActor)

  log.info("Persistence layer top-level actors created")

  // Service layer components
  //
  log.info("Initializing external facing gRPC service")

  val requestServiceActor = RequestServiceActor(persistenceActor, membershipActor)
  RequestServiceImpl(requestServiceActor, membershipActor)

  log.info("Service layer initialized")


  scala.sys.addShutdownHook(membershipActor ! MembershipAPI.DeclareEvent(
    NodeState.DEAD,
    Membership(MembershipActor.nodeID, addressRetriever.selfIP)
  ))
}
