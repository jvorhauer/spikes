package spikes.model

import org.scalactic.TripleEquals.*
import wvlet.airframe.ulid.ULID

import java.time.LocalDateTime
import scala.collection.immutable.HashSet

case class State(
  users: Users               = Users(),
  sessions: Set[UserSession] = HashSet.empty,
  tasks: Map[ULID, Task]     = Map.empty
) {
  def save(u: User): State =  this.copy(users = users.save(u))
  def findUser(email: String): Option[User] = users.find(email)
  def findUser(id: ULID): Option[User] = users.find(id)
  def findUsers(): List[User] = users.ids.values.toList
  def deleteUser(id: ULID): State = this.copy(users = users.remove(id), sessions = sessions.filterNot(_.id === id))
  def getUserResponse(id: ULID): Option[User.Response] = findUser(id)
    .map(_.asResponse)
    .map(ur => ur.copy(tasks = findTasks(ur.id).map(_.asResponse)))

  def login(u: User, expires: LocalDateTime): State = this.copy(sessions = sessions + u.asSession(expires))
  def authorize(token: String): Option[UserSession] = sessions.find(us => us.token === token && us.expires.isAfter(now))
  def authorize(id: ULID): Option[UserSession] = sessions.find(us => us.id === id && us.expires.isAfter(now))
  def logout(id: ULID): State = this.copy(sessions = sessions.filterNot(_.id === id))

  def save(t: Task): State = this.copy(tasks = tasks + (t.id -> t))
  def findTask(id: ULID): Option[Task] = tasks.get(id)
  def findTasks(owner: ULID): Seq[Task] = tasks.values.filter(_.owner === owner).toSeq
  def remTask(id: ULID): State = findTask(id).map(_ => this.copy(tasks = tasks - id)).getOrElse(this)
}
