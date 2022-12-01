package spikes.model

import spikes.Entity
import spikes.model.Status.Status

import java.time.LocalDateTime
import java.util.UUID

object requests {
  trait EntryRequest {
    def id: UUID
    def owner: UUID
    def title: String
    def body: String
  }
}

object Status extends Enumeration {
  type Status = Value
  val Active, Archived, Deleted, ToDo, Doing, Done = Value
}

case class Entry(
  id: UUID,
  owner: UUID,
  created: LocalDateTime,
  status: Status = Status.Active,
  title: String,
  body: String,
  url: Option[String],
  due: Option[LocalDateTime] = None,
  starts: Option[LocalDateTime] = None,
  ends: Option[LocalDateTime] = None
) extends Entity {
  lazy val isTask: Boolean = due.isDefined
  lazy val isEvent: Boolean = starts.isDefined && ends.isDefined
  lazy val isMarker: Boolean = url.isDefined
}
