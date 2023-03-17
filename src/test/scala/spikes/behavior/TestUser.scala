package spikes.behavior

import wvlet.airframe.ulid.ULID

import java.time.LocalDate

trait TestUser {
  val ulid = ULID.newULID
  val name = "Tester"
  val email = "tester@test.er"
  val password = "Welkom123!"
  val born: LocalDate = LocalDate.now().minusYears(21)
}
