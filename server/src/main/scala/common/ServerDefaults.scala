package common

import java.util.concurrent.TimeUnit

import akka.util.Timeout

import scala.concurrent.duration.Duration


object ServerDefaults {

  implicit val EXTERNAL_REQUEST_TIMEOUT: Timeout = Timeout(Duration(20, TimeUnit.SECONDS))
  implicit val INTERNAL_REQUEST_TIMEOUT: Timeout = Timeout(Duration(20, TimeUnit.SECONDS))
  implicit val ACTOR_REQUEST_TIMEOUT: Timeout = Timeout(Duration(2500, TimeUnit.MILLISECONDS))


  // Used to determine how large a buffer of any container of nodes is
  def bufferCapacity(clusterSize: Int): Int =
    Math.max(5, (2.5 * Math.log(clusterSize) + 2).toInt)
}
