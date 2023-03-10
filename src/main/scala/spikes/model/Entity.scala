package spikes.model

import wvlet.airframe.ulid.ULID

trait Entity {
  def id: ULID
}
