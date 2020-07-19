package common.time

import scala.concurrent.duration.FiniteDuration


sealed trait TimerTask[+TimerKey] {
  val key: TimerKey
}


trait TimerTaskHandler[-TimerKey] {

  def processTimerTask(timerTask: TimerTask[TimerKey]): Unit
}


case class SetFixedTimer[TimerKey](
  key: TimerKey,
  timeout: FiniteDuration
) extends TimerTask[TimerKey]


case class SetRandomTimer[TimerKey](
  key: TimerKey,
  timeRange: TimeRange
) extends TimerTask[TimerKey]


case class ResetTimer[TimerKey](key: TimerKey) extends TimerTask[TimerKey]

case class CancelTimer[TimerKey](key: TimerKey) extends TimerTask[TimerKey]


case object CancelAllTimers extends TimerTask[Nothing] {

  override val key: Nothing =
    throw new NoSuchElementException("CancelAllTimers does not contain a specific key type")
}


case object ContinueTimer extends TimerTask[Nothing] {

  override val key: Nothing =
    throw new NoSuchElementException("ContinueTimer does not contain a specific key type")
}