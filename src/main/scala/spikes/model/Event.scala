package spikes.model

import wvlet.airframe.ulid.ULID

import java.time.{LocalDateTime, ZoneId}

trait Event {
  def id: ULID
  lazy val created: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
}
