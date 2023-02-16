package spikes.model

import wvlet.airframe.ulid.ULID

import java.time.{LocalDate, LocalDateTime}
import scala.collection.immutable.HashSet

trait Response

object Response {
  case class User(id: ULID, name: String, email: String, joined: LocalDateTime, born: LocalDate, entries: Set[Entry] = HashSet.empty) extends Response
  case class Info(users: Int, sessions: Int, entries: Int, recovered: Boolean = false) extends Response
  case class Entry(id: ULID, written: LocalDateTime, owner: ULID, title: String, body: String) extends Response
  case class Comment(id: ULID, written: LocalDateTime, entry: ULID, owner: ULID, title: String, body: String) extends Response
}
