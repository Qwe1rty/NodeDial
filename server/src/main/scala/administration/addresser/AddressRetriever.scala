package administration.addresser

import com.risksense.ipaddr.IpAddress
import schema.ImplicitDataConversions._

trait AddressRetriever {

  /**
   * The IP address of this node, where other nodes can use this IP address to contact
   * this node
   *
   * @return the IP address of this node
   */
  def selfIP: IpAddress =
    sys.env("SELF_IP")

  /**
   * The IP address of the seed node for first contact when first joining the cluster
   *
   * @return an optional IpAddress, will be None if the variable was not specified and
   *         implies that this node is the very first node in the cluster
   */
  def seedIP: Option[IpAddress] =
    sys.env.get("SEED_IP").map(IpAddress(_))
}
