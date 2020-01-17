package membership.addresser

import com.risksense.ipaddr.IpAddress
import schema.ImplicitDataConversions._

trait AddressRetriever {

  // Get the IP address of this process so other nodes can reach
  def selfIP: IpAddress =
    sys.env("SELF_IP")

  // Get the IP address of the seed node for first contact when first
  // joining the cluster
  def seedIP: Option[IpAddress] =
    sys.env.get("SEED_IP").map(IpAddress(_))
}
