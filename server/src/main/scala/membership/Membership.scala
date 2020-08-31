package membership

import com.risksense.ipaddr.IpAddress


/**
 * A named tuple that contains the node ID and IP address
 *
 * @param nodeID the node ID
 * @param ipAddress the IP address
 */
case class Membership(nodeID: String, ipAddress: IpAddress) {

  override def toString: String = s"[${nodeID}, ${ipAddress}]"
}
