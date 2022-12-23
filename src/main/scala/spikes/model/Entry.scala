package spikes.model

import java.time.LocalDateTime
import java.util.UUID

trait EntryRequest {
  def id: UUID
  def title: String
  def body: String
}

case class CreateNoteRequest(id: UUID, title: String, body: String) extends EntryRequest
case class CreateMarkerRequest(id: UUID, title: String, body: String, url: String) extends EntryRequest
case class CreateEventRequest(id: UUID, title: String, body: String, starts: LocalDateTime, ends: LocalDateTime) extends EntryRequest
case class CreateReminderRequest(id: UUID, title: String, body: String, due: LocalDateTime) extends EntryRequest

case class Entry(
  id: UUID,
  owner: UUID,
  created: LocalDateTime,
  title: String,
  body: String,
  url: Option[String],
  due: Option[LocalDateTime] = None,
  starts: Option[LocalDateTime] = None,
  ends: Option[LocalDateTime] = None,
  tags: Set[Tag] = Set.empty,
  comments: Set[Comment] = Set.empty
) extends Entity {
  lazy val isTask: Boolean = due.isDefined
  lazy val isEvent: Boolean = starts.isDefined && ends.isDefined
  lazy val isMarker: Boolean = url.isDefined
}
