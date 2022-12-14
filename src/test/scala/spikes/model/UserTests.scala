package spikes.model

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply
import io.scalaland.chimney.dsl.TransformerOps
import net.datafaker.Faker
import org.scalatest.flatspec.AnyFlatSpec
import spikes.validate.ModelValidation.validate

import java.time.{LocalDate, LocalDateTime}
import java.util.{Locale, UUID}

trait TestUser {
  val faker = new Faker(Locale.US)
  val uuid = UUID.randomUUID()
  val name = "Tester"
  val email = "tester@test.er"
  val password = "Welkom123!"
  val born = LocalDate.now().minusYears(21)

  def fakeName: String = faker.name().fullName()
  def fakeEmail: String = faker.internet().emailAddress().replace("@", s"${System.nanoTime()}-@")
  def fakePassword: String = faker.internet().password(8, 64, true, true, true)
  def joined: LocalDateTime = LocalDateTime.now()
}

/*
 * These tests are not to assert that Chimney works but to guarantee no regressions can
 * occur in the core of a system: its domain model.
 */

class UserTests extends AnyFlatSpec with ScalatestRouteTest with TestUser {

  private val testkit = ActorTestKit()
  private val probe = testkit.createTestProbe[StatusReply[UserResponse]]().ref

  "Validate RequestCreateUser" should "correctly check" in {
    val rcu = RequestCreateUser(name, email, password, born)
    assert(rcu.isValid)

    assert(rcu.copy(name = "").isInvalid)
    assert(rcu.copy(email = "").isInvalid)
    assert(validate(rcu, UserRequest.rules).isEmpty)
    assert(rcu.copy(email = "!!!@???.nl").isInvalid)

    assert(RequestCreateUser("", "testerdetest", password, born).isInvalid)

    assert(RequestCreateUser(name, "invalid email address", password, born).isInvalid)

    val invalidPassword = RequestCreateUser(name, email, "niet_welkom", born)
    assert(invalidPassword.isInvalid)

    List("no-digits!", "sh0rt", "no-special-chars12").foreach(s =>
      assert(RequestCreateUser(name, email, s, born).isInvalid)
    )
  }

  "a RequestCreateUser" should "transform to a CreateUser Command" in {
    val rcu = RequestCreateUser(name, email, password, born)
    val cmd = rcu.asCmd(probe)
    assert(cmd.name == rcu.name)
    assert(cmd.email == rcu.email)
    assert(cmd.id != null)
    assert(cmd.password != rcu.password)

    val mod = rcu.copy(name = "Other")
    val too = mod.asCmd(probe)
    assert(too.name == mod.name)
    assert(too.email == mod.email)
    assert(too.id != null && too.id != cmd.id)
  }

  "a RequestUpdateUser" should "transform to a UpdateUser command" in {
    val ruu = RequestUpdateUser(uuid, name, email, password, born)
    val cmd = ruu.asCmd(probe)
    assert(cmd.name == ruu.name)
    assert(cmd.email == ruu.email)
    assert(cmd.id == ruu.id)
  }

  "a RequestDeleteUser" should "transform to a DeleteUser command" in {
    val rdu = RequestDeleteUser(email)
    val cmd = rdu.asCmd(probe)
    assert(cmd.email == rdu.email)
  }

  "a CreateUser command" should "transform to a UserCreated event" in {
    val now = LocalDateTime.now()
    val born = LocalDate.now().minusYears(42)
    val cmd = CreateUser(UUID.randomUUID(), name, email, born, password, probe)
    val evt = cmd.into[UserCreated].withFieldComputed(_.joined, _ => now).transform
    assert(evt.name == cmd.name)
    assert(evt.id == cmd.id)
    assert(evt.email == cmd.email)
    assert(evt.joined == now)
    assert(evt.born == cmd.born && evt.born == born)
    assert(evt.password == cmd.password)
  }
}
