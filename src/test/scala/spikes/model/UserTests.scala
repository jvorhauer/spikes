package spikes.model

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply
import io.scalaland.chimney.dsl.TransformerOps
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.behavior.TestUser
import wvlet.airframe.ulid.ULID

import java.time.{LocalDate, LocalDateTime, ZoneId}
import scala.collection.immutable.HashSet
import scala.concurrent.ExecutionContextExecutor


/*
 * These tests are not to assert that Chimney works but to guarantee no regressions can
 * occur in the core of a system: its domain model.
 */

class UserTests extends AnyFlatSpec with ScalatestRouteTest with Matchers with TestUser {

  private val testkit = ActorTestKit()
  private val probe = testkit.createTestProbe[StatusReply[User.Response]]().ref

  implicit val ec: ExecutionContextExecutor = testkit.system.executionContext


  "Validate RequestCreateUser" should "correctly check" in {

    val rcu = User.Post(name, email, password, born)
    rcu.validated shouldBe empty

    rcu.copy(name, "").validated should have size 1
    rcu.copy(email = "").validated should have size 1
    rcu.copy(name = "").validated should have size 1
    rcu.copy(email = "!!!@???.nl").validated should have size 1

    User.Post("", "testerdetest", password, born).validated should have size 2
    User.Post(name, "invalid email address", password, born).validated should have size 1
    User.Post(name, email, "niet_welkom", born).validated should have size 1

    List("no-digits!", "sh0rt", "no-special-chars12").foreach(s =>
      User.Post(name, email, s, born).validated should have size 1
    )
  }

  "a RequestCreateUser" should "transform to a CreateUser Command" in {
    val rcu = User.Post(name, email, password, born)
    val cmd = rcu.asCmd(probe)
    cmd.name shouldEqual rcu.name
    cmd.email shouldEqual rcu.email
    cmd.password should not equal rcu.password

    val mod = rcu.copy(name = "Other")
    val too = mod.asCmd(probe)
    too.name should be (mod.name)
    too.email should be (mod.email)
    too.id should not be cmd.id
  }

  "a RequestUpdateUser" should "transform to a UpdateUser command" in {
    val ruu = User.Put(ulid, name, password, born)
    val cmd = ruu.asCmd(probe)
    assert(cmd.name == ruu.name)
    assert(cmd.born == ruu.born)
    assert(cmd.id == ruu.id)
  }

  "a RequestDeleteUser" should "transform to a DeleteUser command" in {
    val rdu = User.Delete(email)
    val cmd = rdu.asCmd(probe)
    assert(cmd.email == rdu.email)
  }

  "a CreateUser command" should "transform to a UserCreated event" in {
    val born = LocalDate.now().minusYears(42)
    val cmd = User.Create(ULID.newULID, name, email, password, born, probe)
    val evt = cmd.into[User.Created].transform
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
