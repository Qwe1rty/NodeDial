package membership.addresser

import com.risksense.ipaddr.IpAddress


object KubernetesAddresser extends AddressRetriever {

  // Enabled through K8s:
  // https://stackoverflow.com/questions/30746888/how-to-know-a-pods-own-ip-address-from-inside-a-container-in-the-pod
  override def selfIP: IpAddress = IpAddress(sys.env("MY_POD_IP"))

  override def seedIP: IpAddress = ???
}
