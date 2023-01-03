package spikes.model

import wvlet.airframe.ulid.ULID

import java.time.{LocalDate, LocalDateTime}

trait Response extends CborSerializable

object Response {
  case class User(id: ULID, name: String, email: String, joined: LocalDateTime, born: LocalDate) extends Response
  case class Info(users: Int, sessions: Int, recovered: Boolean = false) extends Response
  case class Entry(id: ULID, title: String, body: String) extends Response
}
