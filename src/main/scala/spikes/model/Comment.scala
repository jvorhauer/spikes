package spikes.model

import spikes.Entity

import java.util.UUID

case class Comment(id: UUID, title: String, body: String) extends Entity
