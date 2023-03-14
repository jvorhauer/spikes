package spikes.behavior

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import spikes.model.{Command, Event}
import wvlet.airframe.ulid.ULID

import scala.concurrent.duration.FiniteDuration


object Reaper {
  def apply(target: ActorRef[Command], after: FiniteDuration): Behavior[Command] = Behaviors.withTimers(new Reaper(_, target, after).idle())

  case class Reap(replyTo: ActorRef[Command]) extends Command
  case class Reaped(id: ULID, eligible: Int) extends Event

  case object Done extends Command
  case object Timeout extends Command
}

class Reaper(timer: TimerScheduler[Command], target: ActorRef[Command], after: FiniteDuration) {

  private def idle(): Behavior[Command] = {
    timer.startSingleTimer(Reaper.Timeout, after)
    active()
  }

  private def active(): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, cmd) =>
      cmd match {
        case Reaper.Timeout =>
          target.tell(Reaper.Reap(ctx.self))
          idle()
        case _ => Behaviors.same
      }
    }
}
