package membership.addresser

import java.net.InetAddress

import com.risksense.ipaddr.IpAddress
import membership.MembershipActor
import org.slf4j.LoggerFactory
import schema.ImplicitDataConversions._

import scala.util.{Failure, Success, Try}


object KubernetesAddresser extends AddressRetriever {

  /**
   * Retrieves the the pod's IP address from the environment variable that's passed down
   * through the Kubernetes Downstream API. Link for reference:
   *
   * stackoverflow.com/questions/30746888/how-to-know-a-pods-own-ip-address-from-inside-a-container-in-the-pod
   *
   * @return the IP address of this pod
   */
  override def selfIP: IpAddress =
    sys.env("MY_POD_IP")

  /**
   * This addresser is purposefully suited to work with a Kubernetes cluster setup.
   *
   * Firstly, this addressor assumes that the database is instantiated in a StatefulSet object.
   *
   * The StatefulSet will assign the first node in the cluster (ordinal = 0) a unique
   * and unchanging hostname. In the StatefulSet configuration, it is possible to pass that
   * hostname as an environment variable.
   *
   * The function will first attempt to read that environment variable as SEED_HOSTNAME. If it
   * exists, it will do a DNS lookup to resolve the IP address.
   *
   * If that variable does not exist (or the DNS lookup fails), then it will then try to directly
   * get the IP address of the seed node through the environment variable SEED_IP, which
   * provides a secondary option for bootstrapping the node if you don't want to configure a
   * DNS service
   *
   * @return an optional IpAddress, will be None if the variable was not specified and
   *         implies that this node is the very first node in the cluster
   */
  override def seedIP: Option[IpAddress] = {

    val log = LoggerFactory.getLogger(MembershipActor.getClass)

    sys.env.get("SEED_HOSTNAME") match {

      case Some(seedHostname) =>
        log.info(s"Retrieved seed node environment variable with value: '${seedHostname}'")

        Try(IpAddress(InetAddress.getByName(seedHostname).getHostAddress)) match {
          case Success(ipAddress) =>
            log.info(s"IP addressed resolved to: ${ipAddress}")
            Some(ipAddress)
          case Failure(e) =>
            log.warn(s"Could not resolve hostname due to error: ${e}")
            None
        }

      case None =>
        log.info("Seed node environment variable not found, attempting to directly get IP address")

        val environmentAddress = super.seedIP
        environmentAddress match {
          case Some(ipAddress) =>
            log.info(s"IP address found through environment variable: ${ipAddress}")
          case None =>
            log.error(s"IP address environment variable not found")
        }
        environmentAddress
    }
  }

}
