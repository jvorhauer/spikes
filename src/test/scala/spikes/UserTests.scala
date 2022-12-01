package inherit

import com.wix.accord._
import io.scalaland.chimney.dsl.TransformerOps
import org.scalatest.flatspec.AnyFlatSpec

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

/*
 * These tests are not to assert that Chimney works but to guarantee no regressions can
 * occur in the core of a system: its domain model.
 */

class UserTests extends AnyFlatSpec with ResultBuilders {

  private val uuid = UUID.randomUUID()
  private val name = "Tester"
  private val email = "tester@test.er"
  private val password = "Welkom123!"
  private val born = LocalDate.now().minusYears(21)

  "Validate RequestCreateUser" should "correctly check" in {
    val rcu = RequestCreateUser(name, email, password, born)
    val result = validate(rcu)
    assert(result.isSuccess)

    assert(validate(rcu.copy(name = "")).isFailure)
    assert(validate(rcu.copy(email = "")).isFailure)
    assert(rcu.isValid)
    assert(!rcu.copy(email = "!!!@???.nl").isValid)

    val emptyName = validate(RequestCreateUser("", "testerdetest", password, born))
    assert(emptyName.isFailure)

    val invalidEmail = validate(RequestCreateUser(name, "invalid email address", password, born))
    assert(invalidEmail.isFailure)

    val invalidPassword = validate(RequestCreateUser(name, email, "niet_welkom", born))
    assert(invalidPassword.isFailure)

    List("no-digits!", "sh0rt", "no-special-chars12").foreach(s =>
      assert(validate(RequestCreateUser(name, email, s, born)).isFailure)
    )

    validate(RequestCreateUser("a!2", "email", "nope", LocalDate.now())) match {
      case Success => println("invalidPassword is ok???")
      case Failure(violations) =>
        violations
          .map(_.asInstanceOf[GroupViolation])
          .map(_.children.flatMap(r => Set(r.asInstanceOf[RuleViolation])))
          .map(_.map(rv => s"${rv.toString}").mkString(", "))
        violations.map(_.toString.replaceFirst("value with value \"RequestCreateUser", "\"")).foreach(println)
    }
  }

  "a RequestCreateUser" should "transform to a CreateUser Command" in {
    val rcu = RequestCreateUser(name, email, password, born)
    val cmd = rcu.asCmd
    assert(cmd.name == rcu.name)
    assert(cmd.email == rcu.email)
    assert(cmd.id != null)
    assert(cmd.password != rcu.password)

    val mod = rcu.copy(name = "Other")
    val too = mod.asCmd
    assert(too.name == mod.name)
    assert(too.email == mod.email)
    assert(too.id != null && too.id != cmd.id)
  }

  "a RequestUpdateUser" should "transform to a UpdateUser command" in {
    val ruu = RequestUpdateUser(uuid, name, email, password, born)
    val cmd = ruu.asCmd
    assert(cmd.name == ruu.name)
    assert(cmd.email == ruu.email)
    assert(cmd.id == ruu.id)
  }

  "a RequestDeleteUser" should "transform to a DeleteUser command" in {
    val rdu = RequestDeleteUser(uuid)
    val cmd = rdu.asCmd
    assert(cmd.id == rdu.id)
  }

  "a CreateUserCommand" should "transform to a UserCreated event" in {
    val now = LocalDateTime.now()
    val cmd = CreateUser(UUID.randomUUID(), name, email, password)
    val evt = cmd.into[UserCreated].withFieldComputed(_.joined, _ => now).transform
    assert(evt.name == cmd.name)
    assert(evt.id == cmd.id)
    assert(evt.email == cmd.email)
    assert(evt.joined == now)
  }
}
