package spikes.db

import spikes.SpikesTest
import spikes.model.{Comment, Entry, User}
import wvlet.airframe.ulid.ULID

import java.time.LocalDate

class RepositoryTests extends SpikesTest {

  "save a new user" should "return 1" in {
    val user = User(ULID.newULID, "Tester1", "test1@tester.nl", "Test123", LocalDate.now())
    Repository.save(user) shouldEqual 1
    Repository.userCount() shouldEqual 1
    Repository.findUser(user.id) should not be empty
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
    Repository.userCount() shouldEqual 1

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
    found.entries.head.owner shouldEqual user.id
  }

  "save and findWithEntries" should "return user with entries" in {
    val user = User(ULID.newULID, "TestWithEntries", "test-with-entries@tester.nl", "Test123", LocalDate.now())
    Repository.save(user) shouldEqual 1

    val e1 = Entry(ULID.newULID, user.id, "1. entry title", "entry body")
    Repository.save(e1) shouldEqual 1

    val e2 = Entry(ULID.newULID, user.id, "2. entry title", "entry body")
    Repository.save(e2) shouldEqual 1

    val o = Repository.findUser(user.id)
    o should not be empty
    val found = o.get
    found.entries should have size 2
  }

  "save and findEntry" should "return entry with comments" in {
    val user = User(ULID.newULID, "TestWithComments", "test-with-comments@tester.nl", "Test123", LocalDate.now())
    Repository.save(user) shouldEqual 1

    val e1 = Entry(ULID.newULID, user.id, "entry title", "entry with comments")
    Repository.save(e1) shouldEqual 1

    val c1 = Comment(ULID.newULID, e1.id, user.id, "comment title", "comment body")
    Repository.save(c1) shouldEqual 1

    val o = Repository.findEntry(e1.id)
    o should not be empty
    val found = o.get
    found.comments should have size 1
  }
}
