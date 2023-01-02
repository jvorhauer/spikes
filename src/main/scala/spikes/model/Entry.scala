package spikes.model

import wvlet.airframe.ulid.ULID

import java.time.LocalDateTime

trait EntryRequest {
  def id: ULID
  def title: String
  def body: String
}

case class CreateNoteRequest(id: ULID, title: String, body: String) extends EntryRequest
case class CreateMarkerRequest(id: ULID, title: String, body: String, url: String) extends EntryRequest
case class CreateEventRequest(id: ULID, title: String, body: String, starts: LocalDateTime, ends: LocalDateTime) extends EntryRequest
case class CreateReminderRequest(id: ULID, title: String, body: String, due: LocalDateTime) extends EntryRequest

case class Entry(
  id: ULID,
  owner: ULID,
  title: String,
  body: String,
  url: Option[String] = None,
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
