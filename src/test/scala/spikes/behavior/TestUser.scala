package spikes.behavior

import spikes.model.today
import wvlet.airframe.ulid.ULID

import java.time.LocalDate

trait TestUser {
  val ulid = ULID.newULID
  val name = "Tester"
  val email = "tester@test.er"
  val password = "Welkom123!"
  val born: LocalDate = today.minusYears(21)
}
