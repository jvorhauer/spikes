package spikes.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import spikes.db.Repository
import spikes.model.Event._
import spikes.model.{Event, User}
import wvlet.airframe.ulid.ULID


object Finder {

  private var sessions: Int = 0

  def apply(): Behavior[Event] =
    Behaviors.receive {
      case (ctx, evt) =>
        evt match {
          case uc: UserCreated => Repository.save(uc.asEntity)
          case uu: UserUpdated => Repository.findUser(uu.id).map(_.copy(name = uu.name, born = uu.born)).foreach(Repository.save)
          case ud: UserDeleted => Repository.deleteUser(ud.id)

          case _: LoggedIn     => sessions += 1
          case _: LoggedOut    => sessions -= 1
          case r: Reaped       => sessions -= r.eligible

          case ec: EntryCreated   => Repository.save(ec.asEntity)
          case cc: CommentCreated => Repository.save(cc.asEntity)

          case x => ctx.log.debug(s"Finder: ${x}")
        }
        ctx.log.info(s"finder.apply: ${Repository.userCount()} user(s) / $sessions session(s)")
        Behaviors.same
      case x =>
        println(s"Finder: x = ${x}")
        Behaviors.unhandled
    }

  def findUser(id: ULID): Option[User] = Repository.findUser(id)
  def findUsers(page: Int = 0, psize: Int = 20): Seq[User] = Repository.findUsers(page * psize, psize)
}
