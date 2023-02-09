package spikes.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import spikes.RelationType
import spikes.model.{Entity, Entry, Event, User}
import spikes.model.Event._
import wvlet.airframe.ulid.ULID

import scala.collection.mutable

object Finder {

  private var users: Int = 0
  private var sessions: Int = 0
  private var entries: Int = 0

  private val entities = mutable.SortedMap.empty[ULID, Entity]
  private val emails = mutable.Map.empty[String, User]
  private val relator = mutable.MultiDict.empty[ULID, ULID] // entityId -> relationId
  private val relations = mutable.Map.empty[ULID, Relation]

  def apply(): Behavior[Event] =
    Behaviors.receive {
      case (ctx, evt) =>
        evt match {
          case uc: UserCreated  =>
            entities.addOne(uc.id -> uc.asEntity)
            emails.addOne(uc.email -> uc.asEntity)
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
            findUser(ud.id).map(_.email).foreach(emails.remove)
            users -= 1

          case _: LoggedIn     => sessions += 1
          case _: LoggedOut    => sessions -= 1
          case r: Reaped       => sessions -= r.eligible

          case ec: EntryCreated =>
            entities.addOne(ec.id -> ec.asEntity)
            val relation = Relation(ULID.newULID, ec.id, RelationType.WROTE)
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
  def findUser(id: ULID): Option[User] = find(id)
    .filter(_.isInstanceOf[User])
    .map(_.asInstanceOf[User])
    .map(u => u.copy(entries = findEntries(u.id)))
  def findUser(email: String): Option[User] = emails.get(email)
  def findUsers(page: Int = 1, psize: Int = 20): List[User] = entities
    .filter(_._2.isInstanceOf[User])
    .slice(page * psize, page * psize + psize)
    .values
    .map(_.asInstanceOf[User])
    .map(u => u.copy(entries = findEntries(u.id)))
    .toList

  def findEntry(id: ULID): Option[Entry] = find(id).filter(_.isInstanceOf[Entry]).map(_.asInstanceOf[Entry])
  def findEntries(userId: ULID): Seq[Entry] = {
    relator.filter(_._1 == userId)
      .values
      .flatMap(r => relations.get(r))
      .flatMap(r => findEntry(r.eid))
      .toSeq
  }

  case class Relation(id: ULID, eid: ULID, relationType: RelationType)
}
