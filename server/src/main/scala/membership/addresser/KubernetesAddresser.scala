package membership.addresser

import java.net.InetAddress

import com.risksense.ipaddr.IpAddress
import membership.MembershipActor
import org.slf4j.LoggerFactory
import schema.ImplicitDataConversions._

import scala.util.{Failure, Success, Try}


object KubernetesAddresser extends AddressRetriever {

  // Enabled through K8s:
  // https://stackoverflow.com/questions/30746888/how-to-know-a-pods-own-ip-address-from-inside-a-container-in-the-pod
  override def selfIP: IpAddress =
    sys.env("MY_POD_IP")

  override def seedIP: Option[IpAddress] = {

    val log = LoggerFactory.getLogger(MembershipActor.getClass)

    sys.env.get("SEED_NODE") match {

      case Some(seedHostname) =>
        log.info(s"Retrieved seed node environment variable with value: '${seedHostname}'")

        Try(IpAddress(InetAddress.getByName(seedHostname).getHostAddress)) match {
          case Success(ipAddress) => Some(ipAddress)
          case Failure(e) =>
            log.error(s"Could not resolve hostname: ${e}")
            None
        }

      case None =>
        log.info("Seed node environment variable was not found")
        None
    }
  }

}
