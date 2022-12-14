package spikes.model

import spikes.{Command, Entity, Event, Request}

import java.util.UUID

case class CreateTagRequest(id: UUID, title: String) extends Request

case class CreateTag(id: UUID, title: String) extends Command

case class TagCreated(id: UUID, title: String) extends Event

case class Tag(id: UUID, title: String) extends Entity
