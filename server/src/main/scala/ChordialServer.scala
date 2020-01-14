import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import common.ChordialConstants._
import common.membership.{Membership, MembershipAPI, MembershipActor}
import common.membership.addresser.KubernetesAddresser
import common.membership.failureDetection.{FailureDetectorActor, FailureDetectorServiceImpl}
import common.membership.types.NodeState
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


  /*
   * Persistence layer components
   */
  log.info("Initializing top-level persistence layer components")

  val threadPartitionActor = ThreadPartitionActor()
  val persistenceActor = PersistenceActor(threadPartitionActor)

  log.info("Persistence layer top-level actors created")

  /*
   * Membership layer components
   *   TODO eventually allow different address retriever methods
   */
  log.info("Initializing membership layer components")

  val addressRetriever = KubernetesAddresser
  val membershipActor = MembershipActor(addressRetriever, REQUIRED_TRIGGERS)

  val failureDetectorActor = FailureDetectorActor(membershipActor)
  FailureDetectorServiceImpl()

  /*
   * Service layer components
   */
  log.info("Initializing external facing gRPC service")

  val requestServiceActor = RequestServiceActor(persistenceActor)
  RequestServiceImpl(requestServiceActor)

  log.info("Service layer initialized")


  scala.sys.addShutdownHook(membershipActor ! MembershipAPI.DeclareEvent(
    NodeState.DEAD,
    Membership(MembershipActor.nodeID, addressRetriever.selfIP)
  ))
}
