package spikes.db

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.model.{Entry, User}
import wvlet.airframe.ulid.ULID

import java.time.LocalDate

class RepositoryTests extends AnyFlatSpec with Matchers {

  Repository.init()

  "save a new user" should "return 1" in {
    val user = User(ULID.newULID, "Tester1", "test1@tester.nl", "Test123", LocalDate.now())
    Repository.save(user) shouldEqual 1
  }

  "save a new entry for a new user" should "return 1" in {
    val user = User(ULID.newULID, "Tester2", "test2@tester.nl", "Test123", LocalDate.now())
    Repository.save(user) shouldEqual 1

    val entry = Entry(ULID.newULID, user.id, "Test Ttile", "Test Body")
    Repository.save(entry) shouldEqual 1
  }

  "save and update a user" should "return 1 twice" in {
    val user = User(ULID.newULID, "Tester22", "test22@tester.nl", "Test123", LocalDate.now())
    Repository.save(user) shouldEqual 1

    Repository.save(user.copy(name = "Tester222")) shouldEqual 1
    val oupdated = Repository.findUser(user.id)
    oupdated should not be empty
    oupdated.get.name shouldEqual "Tester222"
    oupdated.get.email shouldEqual "test22@tester.nl"
  }

  "save and find a user with no entries" should "return user" in {
    val user = User(ULID.newULID, "Tester3", "test3@tester.nl", "Test123", LocalDate.now())
    Repository.save(user) shouldEqual 1

    val ofound = Repository.findUser(user.id)
    ofound should not be empty
    val found = ofound.get
    found.name shouldEqual "Tester3"
    found.entries shouldBe empty
  }

  "save and find a user with one entry" should "return user" in {
    val user = User(ULID.newULID, "Tester4", "test4@tester.nl", "Test123", LocalDate.now())
    Repository.save(user) shouldEqual 1

    val entry = Entry(ULID.newULID, user.id, "Test Ttile", "Test Body")
    Repository.save(entry) shouldEqual 1

    val ofound = Repository.findUser(user.id)
    ofound should not be empty
    val found = ofound.get
    found.name shouldEqual "Tester4"
    found.entries should have size 1
  }
}
