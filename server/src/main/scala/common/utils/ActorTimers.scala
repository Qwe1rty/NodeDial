package common.utils

import akka.actor.Timers

import scala.concurrent.duration.FiniteDuration


object ActorTimers {

  private case object TimerKey

  case object Tick
}


trait ActorTimers extends Timers {

  import ActorTimers._

  def start(delay: FiniteDuration): Unit =
    timers.startTimerWithFixedDelay(TimerKey, Tick, delay)

  def stop(): Unit = timers.cancel(TimerKey)
}
