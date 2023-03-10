package spikes.behavior

import net.datafaker.Faker
import spikes.model.User
import wvlet.airframe.ulid.ULID

import java.time.LocalDate
import java.util.Locale

trait TestUser {
  val faker = new Faker(Locale.US)
  val ulid = ULID.newULID
  val name = "Tester"
  val email = "tester@test.er"
  val password = "Welkom123!"
  val born: LocalDate = LocalDate.now().minusYears(21)

  def fakeName: String = faker.name().fullName()
  def fakeEmail: String = faker.internet().emailAddress().replace("@", s"-${System.nanoTime()}@")
  def fakePassword: String = faker.internet().password(8, 64, true, true, true)
}

object TestUser {
  val empty: User = User(ULID.newULID, "", "", "", LocalDate.now())
}
