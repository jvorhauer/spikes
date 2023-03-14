package spikes.model

import org.scalactic.TripleEquals.*
import wvlet.airframe.ulid.ULID

case class Users(ids: Map[ULID, User] = Map.empty, emails: Map[String, User] = Map.empty) {
  def save(u: User): Users = Users(ids + (u.id -> u), emails + (u.email -> u))
  def find(id: ULID): Option[User] = ids.get(id)
  def find(email: String): Option[User] = emails.get(email)
  def remove(id: ULID): Users = find(id).map(u => Users(ids - u.id, emails - u.email)).getOrElse(this)
  def concat(other: Users): Users = Users(ids ++ other.ids, emails ++ other.emails)
  def all(): List[User] = ids.values.toList

  def size: Int = ids.size
  def valid: Boolean = ids.size === emails.size
}
