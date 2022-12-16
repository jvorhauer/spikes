package spikes.behavior

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import spikes.Command
import spikes.model.Reap

import scala.concurrent.duration.FiniteDuration

case object Done extends Command
case object Timeout extends Command

object Reaper {
  def apply(target: ActorRef[Command], after: FiniteDuration): Behavior[Command] =
    Behaviors.withTimers(new Reaper(_, target, after).idle())
}

class Reaper(timer: TimerScheduler[Command], target: ActorRef[Command], after: FiniteDuration) {

  private def idle(): Behavior[Command] = {
    timer.startSingleTimer(Timeout, after)
    active()
  }

  private def active(): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, cmd) =>
      cmd match {
        case Timeout =>
          target.tell(Reap(ctx.self))
          idle()
        case _ => Behaviors.same
      }
    }
}
