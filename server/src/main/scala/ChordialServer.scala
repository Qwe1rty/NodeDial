import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import common.modules.addresser.KubernetesAddresser
import common.modules.failureDetection.FailureDetectorActor
import common.modules.membership.MembershipActor
import org.slf4j.LoggerFactory
import persistence.io.PersistenceActor
import persistence.threading.ThreadPartitionActor
import schema.service.RequestService
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
  val membershipActor = MembershipActor(addressRetriever)

  val failureDetectorActor = FailureDetectorActor(membershipActor)

  /*
   * Service layer components
   */
  log.info("Initializing external facing gRPC service")

  val requestServiceActor = RequestServiceActor(persistenceActor)
  val _: RequestService = RequestServiceImpl(requestServiceActor)

  log.info("Service layer initialized")
}
