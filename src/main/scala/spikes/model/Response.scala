package spikes.model

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

trait Response extends CborSerializable

object Response {
  case class User(id: UUID, name: String, email: String, joined: LocalDateTime, born: LocalDate) extends Response
}
