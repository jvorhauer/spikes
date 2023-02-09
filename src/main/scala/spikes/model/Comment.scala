package spikes.model

import wvlet.airframe.ulid.ULID

case class Comment(id: ULID, entry: ULID, owner: ULID, title: String, body: String) extends Entity {
  def this(t: (ULID, ULID, ULID, String, String)) = this(t._1, t._2, t._3, t._4, t._5)
  lazy val asTuple: (ULID, ULID, ULID, String, String) = (id, entry, owner, title, body)
}
