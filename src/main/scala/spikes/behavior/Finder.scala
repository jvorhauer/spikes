package spikes.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import spikes.model.{Entity, Event, User}
import spikes.model.Event._
import spikes.validate.Regexes.email
import wvlet.airframe.ulid.ULID

import scala.collection.mutable

object Finder {

  private var users: Int = 0
  private var sessions: Int = 0
  private var entries: Int = 0

  private val entities = mutable.Map.empty[ULID, Entity]
  private val relator = mutable.MultiDict.empty[ULID, ULID]
  private val relations = mutable.Map.empty[ULID, Relation]

  def apply(): Behavior[Event] =
    Behaviors.receive {
      case (ctx, evt) =>
        evt match {
          case uc: UserCreated  =>
            entities.addOne(uc.id -> uc.asEntity)
            users += 1

          case uu: UserUpdated =>
            entities.get(uu.id) match {
              case Some(ent) if ent.isInstanceOf[User] =>
                val u = ent.asInstanceOf[User]
                entities.put(uu.id, u.copy(name = uu.name, password = uu.password, born = uu.born))
              case _ =>
            }

          case ud: UserDeleted  =>
            relator.removeKey(ud.id)
            entities.remove(ud.id)
            users -= 1

          case _: LoggedIn     => sessions += 1
          case _: LoggedOut    => sessions -= 1
          case r: Reaped       => sessions -= r.eligible

          case ec: EntryCreated =>
            entities.addOne(ec.id -> ec.asEntity)
            val relation = Relation(ULID.newULID, ec.id, "HAS")
            relations.addOne(relation.id, relation)
            relator.addOne(ec.owner -> relation.id)
            entries += 1

          case x => ctx.log.debug(s"Finder: ${x}")
        }
        ctx.log.info(s"Finder: $users / $sessions / $entries")
        Behaviors.same
      case x =>
        println(s"Finder: x = ${x}")
        Behaviors.unhandled
    }

  def find(id: ULID): Option[Entity] = entities.get(id)
  def findUser(id: ULID): Option[User] = entities.get(id).filter(_.isInstanceOf[User]).map(_.asInstanceOf[User])

  case class Relation(id: ULID, eid: ULID, relationType: String)
}
