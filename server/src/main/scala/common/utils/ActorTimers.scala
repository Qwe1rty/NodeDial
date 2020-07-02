package common.utils

import akka.actor.Timers

import scala.concurrent.duration.FiniteDuration


object ActorTimers {

  /**
   * The timer key used internally for identification
   */
  private case object TimerKey

  /**
   * The default tick object that will be sent to the actor
   */
  case object Tick
}


trait ActorTimers extends Timers {

  import ActorTimers._

  /**
   * Overridable signal object that the timer sends to the actor
   */
  val timerSignal: Any = Tick


  def startPeriodic(delay: FiniteDuration, key: Any = TimerKey): Unit =
    timers.startTimerWithFixedDelay(TimerKey, timerSignal, delay)

  def startSingle(delay: FiniteDuration, key: Any = TimerKey): Unit =
    timers.startSingleTimer(TimerKey, timerSignal, delay)

  def stop(key: Any = TimerKey): Unit = timers.cancel(TimerKey)
}
