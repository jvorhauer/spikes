package spikes.model

import java.util.UUID

case class Comment(id: UUID, title: String, body: String) extends Entity
