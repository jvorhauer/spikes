package spikes.validate

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.LocalDate

class RulesTests extends AnyFlatSpec with Matchers {
  case class TestMe(name: String, email: String, password: String, born: LocalDate = LocalDate.now())

  val testKees= TestMe("Kees", "kees@hier.nu", "Welkom123!", LocalDate.now().minusYears(23))

  "TestMe" should "validate" in {
    var errors = Validation.validate(testKees, Rules.createUser)
    errors should have size(0)
    errors shouldBe empty

    errors = Validation.validate(TestMe("", "", ""), Rules.createUser)
    errors should have size(4)

    errors = Validation.validate(testKees.copy(name = "Vlad dë Émpaïlér"), Rules.createUser)
    errors shouldBe empty

  }
}
