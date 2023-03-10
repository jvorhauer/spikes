package spikes.model

import wvlet.airframe.ulid.ULID

case class Group(id: ULID, name: String, description: String, users: Seq[User]) extends Entity

object Group {
  // TODO: requests: post, put, delete
  // TODO: commands: create, update, remove
  // TODO: events: created, updated, removed
  // TODO: respons: response (without users)
}
