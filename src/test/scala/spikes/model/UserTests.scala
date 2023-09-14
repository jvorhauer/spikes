package spikes.model

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.behavior.TestUser

import java.time.LocalDateTime
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


  "Validate RequestPostUser" should "correctly check" in {

    val rcp = User.Post(name, email, password, born)
    rcp.validated shouldBe empty

    rcp.copy(name, "").validated should have size 1
    rcp.copy(email = "").validated should have size 1
    rcp.copy(name = "").validated should have size 1
    rcp.copy(email = "!!!@???.nl").validated should have size 1

    User.Post("", "testerdetest", password, born).validated should have size 2
    User.Post(name, "invalid email address", password, born).validated should have size 1
    User.Post(name, email, "niet_welkom", born).validated should have size 1

    List("no-digits!", "sh0rt", "no-special-chars12").foreach(s =>
      User.Post(name, email, s, born).validated should have size 1
    )
  }

  "a RequestPostUser" should "transform to a CreateUser Command" in {
    val rcp = User.Post(name, email, password, born)
    val cmd = rcp.asCmd(probe)
    cmd.name shouldEqual rcp.name
    cmd.email shouldEqual rcp.email
    cmd.password should not equal rcp.password

    val mod = rcp.copy(name = "Other")
    val too = mod.asCmd(probe)
    too.id should not be cmd.id
    too.name should be (mod.name)
    too.email should be (mod.email)
  }

  "a RequestPutUser" should "transform to a UpdateUser command" in {
    val ruu = User.Put(id, Some(name), Some(password), Some(born))
    ruu.validated should be (empty)
    val cmd = ruu.asCmd(probe)
    cmd.name should be (ruu.name)
    cmd.born should be (ruu.born)
    cmd.id should be (ruu.id)
  }

  "a CreateUser command" should "transform to a UserCreated event" in {
    val born = today.minusYears(42)
    val cmd = User.Create(next, name, email, password, born, Some("It's me!!"), probe)
    val evt = cmd.asEvent
    assert(evt.name === cmd.name)
    assert(evt.id === cmd.id)
    assert(evt.email === cmd.email)
    assert(evt.id.created === LocalDateTime.ofInstant(evt.id.getInstant, zone))
    assert(evt.born === cmd.born && evt.born === born)
    assert(evt.password === cmd.password)
  }

  "Set" should "allow removal of not-there element" in {
    val set: Set[String] = HashSet("one", "two", "four")
    set should have size 3
    set - "one" should have size 2
    set - "three" should have size 3
  }
}
