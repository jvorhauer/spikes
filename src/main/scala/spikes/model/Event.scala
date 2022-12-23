package spikes.model

import io.scalaland.chimney.dsl.TransformerOps

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

trait Event extends CborSerializable

object Event {
  case class UserCreated(
    id: UUID, name: String, email: String, password: String, joined: LocalDateTime, born: LocalDate
  ) extends Event {
    lazy val asEntity = this.into[User].transform
  }

  case class UserUpdated(id: UUID, name: String, password: String, born: LocalDate) extends Event

  case class UserDeleted(email: String) extends Event

  case class LoggedIn(email: String, expires: LocalDateTime = LocalDateTime.now().plusHours(2)) extends Event

  case class Reaped(eligible: Int, performed: LocalDateTime = LocalDateTime.now()) extends Event
}
