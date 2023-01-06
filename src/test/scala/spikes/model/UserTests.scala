package spikes.model

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply
import io.scalaland.chimney.dsl.TransformerOps
import net.datafaker.Faker
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.validate.Rules
import spikes.validate.Validation.validate
import wvlet.airframe.ulid.ULID

import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.util.Locale
import scala.collection.immutable.HashSet

trait TestUser {
  val faker = new Faker(Locale.US)
  val ulid = ULID.newULID
  val name = "Tester"
  val email = "tester@test.er"
  val password = "Welkom123!"
  val born = LocalDate.now().minusYears(21)

  def fakeName: String = faker.name().fullName()
  def fakeEmail: String = faker.internet().emailAddress().replace("@", s"-${System.nanoTime()}@")
  def fakePassword: String = faker.internet().password(8, 64, true, true, true)
}

object TestUser {
  val empty = User(ULID.newULID, "", "", "", LocalDate.now())
}

/*
 * These tests are not to assert that Chimney works but to guarantee no regressions can
 * occur in the core of a system: its domain model.
 */

class UserTests extends AnyFlatSpec with ScalatestRouteTest with Matchers with TestUser {

  private val testkit = ActorTestKit()
  private val probe = testkit.createTestProbe[StatusReply[Response.User]]().ref

  implicit val ec = testkit.system.executionContext


  "Validate RequestCreateUser" should "correctly check" in {
    val rcu = Request.CreateUser(name, email, password, born)
    rcu.valid should be (true)

    rcu.copy(name, "").valid should be (false)
    rcu.copy(email = "").valid should be (false)
    validate(rcu, Rules.createUser) shouldBe empty
    rcu.copy(email = "!!!@???.nl").valid should be (false)

    Request.CreateUser("", "testerdetest", password, born).valid should be (false)
    Request.CreateUser(name, "invalid email address", password, born).valid should be (false)
    Request.CreateUser(name, email, "niet_welkom", born).valid should be (false)

    List("no-digits!", "sh0rt", "no-special-chars12").foreach(s =>
      Request.CreateUser(name, email, s, born).valid should be (false)
    )
  }

  "a RequestCreateUser" should "transform to a CreateUser Command" in {
    val rcu = Request.CreateUser(name, email, password, born)
    val cmd = rcu.asCmd(probe)
    cmd.name shouldEqual rcu.name
    cmd.email shouldEqual rcu.email
    cmd.id should not be null
    cmd.password should not equal rcu.password

    val mod = rcu.copy(name = "Other")
    val too = mod.asCmd(probe)
    assert(too.name == mod.name)
    assert(too.email == mod.email)
    assert(too.id != null && too.id != cmd.id)
  }

  "a RequestUpdateUser" should "transform to a UpdateUser command" in {
    val ruu = Request.UpdateUser(ulid, name, password, born)
    val cmd = ruu.asCmd(probe)
    assert(cmd.name == ruu.name)
    assert(cmd.born == ruu.born)
    assert(cmd.id == ruu.id)
  }

  "a RequestDeleteUser" should "transform to a DeleteUser command" in {
    val rdu = Request.DeleteUser(email)
    val cmd = rdu.asCmd(probe)
    assert(cmd.email == rdu.email)
  }

  "a CreateUser command" should "transform to a UserCreated event" in {
    val born = LocalDate.now().minusYears(42)
    val cmd = Command.CreateUser(ULID.newULID, name, email, born, password, probe)
    val evt = cmd.into[Event.UserCreated].transform
    assert(evt.name == cmd.name)
    assert(evt.id == cmd.id)
    assert(evt.email == cmd.email)
    assert(evt.created == LocalDateTime.ofInstant(evt.id.toInstant, ZoneId.of("UTC")))
    assert(evt.born == cmd.born && evt.born == born)
    assert(evt.password == cmd.password)
  }

  "Set" should "allow removal of not-there element" in {
    val set: Set[String] = HashSet("one", "two", "four")
    set should have size 3
    set - "one" should have size 2
    set - "three" should have size 3
  }
}
