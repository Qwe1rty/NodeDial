package common.time

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.util.Random


/**
 * Defines a range of time
 *
 * @param lowerBound lower time bound
 * @param upperBound upper time bound
 */
case class TimeRange(lowerBound: FiniteDuration, upperBound: FiniteDuration) {

  if (lowerBound > upperBound)
    throw new IllegalArgumentException("Upper time bound cannot be lower than the lower time bound")


  def range(): FiniteDuration = {
    upperBound - lowerBound
  }

  def between(duration: FiniteDuration): Boolean = {
    lowerBound <= duration && duration <= upperBound
  }

  def random(): FiniteDuration = {
    FiniteDuration(Random.between(lowerBound.toNanos, upperBound.toNanos), TimeUnit.NANOSECONDS)
  }
}

