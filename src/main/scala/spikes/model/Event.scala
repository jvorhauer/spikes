package spikes.model

import io.scalaland.chimney.dsl.TransformerOps
import wvlet.airframe.ulid.ULID
import spikes.model.Status._

import java.time.{LocalDate, LocalDateTime, ZoneId}

trait Event {
  def id: ULID
  lazy val created: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
}

object Event {
  case class UserCreated(
    id: ULID, name: String, email: String, password: String, born: LocalDate
  ) extends Event {
    lazy val asEntity: User = this.into[User].transform
  }
  case class UserUpdated(id: ULID, name: String, password: String, born: LocalDate) extends Event
  case class UserDeleted(id: ULID) extends Event

  case class LoggedIn(id: ULID, expires: LocalDateTime = now.plusHours(2)) extends Event
  case class Refreshed(id: ULID, expires: LocalDateTime = now.plusHours(2)) extends Event
  case class LoggedOut(id: ULID) extends Event

  case class Reaped(id: ULID, eligible: Int) extends Event

  case class EntryCreated(
    id: ULID, 
    owner: ULID, 
    title: String, 
    body: String,
    status: Status = Status.Blank,
    url: Option[String] = None,
    due: Option[LocalDateTime] = None,
    starts: Option[LocalDateTime] = None,
    ends: Option[LocalDateTime] = None
  ) extends Event {
    lazy val asEntity: Entry = this.into[Entry].transform
  }
}
