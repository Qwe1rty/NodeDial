package membership.addresser

import com.risksense.ipaddr.IpAddress


trait AddressRetriever {

  // Get the IP address of this process so other nodes can reach
  def selfIP: IpAddress

  // Get the IP address of the seed node for first contact when first
  // joining the cluster
  def seedIP: IpAddress
}
