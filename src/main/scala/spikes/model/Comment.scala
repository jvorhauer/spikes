package spikes.model

import wvlet.airframe.ulid.ULID

case class Comment(id: ULID, title: String, body: String) extends Entity
