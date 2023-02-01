package spikes.model

import wvlet.airframe.ulid.ULID

import java.time.{LocalDateTime, ZoneId}

trait Entity {
  def id: ULID
  def created: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
}
