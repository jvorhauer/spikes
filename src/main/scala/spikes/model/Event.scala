package spikes.model

import io.scalaland.chimney.dsl.TransformerOps
import wvlet.airframe.ulid.ULID

import java.time.{LocalDate, LocalDateTime, ZoneId}

trait Event extends CborSerializable {
  def id: ULID
  lazy val created = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
}

object Event {
  case class UserCreated(
    id: ULID, name: String, email: String, password: String, born: LocalDate
  ) extends Event {
    lazy val asEntity = this.into[User].transform
  }
  case class UserUpdated(id: ULID, name: String, password: String, born: LocalDate) extends Event
  case class UserDeleted(id: ULID) extends Event

  case class LoggedIn(id: ULID, expires: LocalDateTime = LocalDateTime.now().plusHours(2)) extends Event
  case class LoggedOut(id: ULID) extends Event

  case class Reaped(id: ULID, eligible: Int, performed: LocalDateTime = LocalDateTime.now()) extends Event
}
