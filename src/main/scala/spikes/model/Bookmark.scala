package spikes.model

import wvlet.airframe.ulid.ULID

import java.time.LocalDate

case class Bookmark(id: ULID, owner: ULID, title: String, body: String, url: String, check: LocalDate) extends Entity

object Bookmark {
  // TODO: requests = post, put, delete
  // TODO: commands = create, update, remove
  // TODO: events = created, updated, removed
  // TODO: respons = response
}
