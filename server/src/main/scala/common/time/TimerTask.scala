package common.time

import scala.concurrent.duration.FiniteDuration


sealed trait TimerTask[+TimerKey] {
  val key: TimerKey
}


trait TimerTaskHandler[-TimerKey] {

  def handleTimerTask(timerTask: TimerTask[TimerKey]): Unit
}


case class SetFixedTimer[TimerKey](
  key: TimerKey,
  timeout: FiniteDuration
) extends TimerTask[TimerKey]


case class SetRandomTimer[TimerKey](
  key: TimerKey,
  lowerTimeout: FiniteDuration,
  upperTimeout: FiniteDuration
) extends TimerTask[TimerKey]


case class ResetTimer[TimerKey](key: TimerKey) extends TimerTask[TimerKey]

case class CancelTimer[TimerKey](key: TimerKey) extends TimerTask[TimerKey]


case object CancelAllTimers extends TimerTask[Nothing] {

  override val key: Nothing =
    throw new NoSuchElementException("CancelAllTimers does not contain a specific key type")
}