package common

import java.util.concurrent.TimeUnit

import akka.util.Timeout

import scala.concurrent.duration.Duration


object ChordialDefaults {

  implicit val EXTERNAL_REQUEST_TIMEOUT: Timeout = Timeout(Duration(10, TimeUnit.SECONDS))
  implicit val INTERNAL_REQUEST_TIMEOUT: Timeout = Timeout(Duration(20, TimeUnit.SECONDS))


  // Used to determine how large a buffer of any container of nodes is
  def bufferCapacity(clusterSize: Int): Int =
    (2.5 * Math.log(clusterSize) + 0.5).toInt
}
