package spikes.behavior

import spikes.model.{next, today}

import java.time.LocalDate

trait TestUser {
  val id = next
  val name = "Tester"
  val email = "tester@test.er"
  val password = "Welkom123!"
  val born: LocalDate = today.minusYears(21)
}
