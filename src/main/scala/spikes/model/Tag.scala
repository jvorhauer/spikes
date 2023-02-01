package spikes.model

import wvlet.airframe.ulid.ULID

case class CreateTagRequest(title: String) extends Request {

}


case class TagCreated(id: ULID, title: String) extends Event

case class Tag(id: ULID, title: String) extends Entity
