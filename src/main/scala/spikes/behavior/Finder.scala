package spikes.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import spikes.model.Event
import spikes.model.Event._

object Finder {

  private var users: Int = 0
  private var sessions: Int = 0
  private var entries: Int = 0

  def apply(): Behavior[Event] =
    Behaviors.receive {
      case (ctx, evt) =>
        evt match {
          case _: UserCreated  => users += 1
          case _: UserDeleted  => users -= 1
          case _: LoggedIn     => sessions += 1
          case _: LoggedOut    => sessions -= 1
          case r: Reaped       => sessions -= r.eligible
          case _: EntryCreated => entries += 1
          case x => ctx.log.debug(s"Finder: ${x}")
        }
        ctx.log.info(s"Finder: $users / $sessions / $entries")
        Behaviors.same
      case x =>
        println(s"Finder: x = ${x}")
        Behaviors.unhandled
    }
}
