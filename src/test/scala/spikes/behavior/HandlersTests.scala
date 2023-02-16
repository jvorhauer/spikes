package spikes.behavior

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.persistence.testkit.scaladsl.UnpersistentBehavior
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import spikes.SpikesTest
import spikes.db.Repository
import spikes.model.Event.LoggedIn
import spikes.model._
import wvlet.airframe.ulid.ULID

import java.time.LocalDate
import java.util.UUID

class HandlersTests extends SpikesTest with BeforeAndAfterAll {

  private val name = "Tester"
  private val born = LocalDate.now().minusYears(21)
  private val password = "Welkom123!"

  val testKit: ActorTestKit = ActorTestKit(
    ConfigFactory.parseString(
      s"""akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
          akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
          akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID()}"
          akka.loggers = ["akka.event.Logging$$DefaultLogger"]
          akka.loglevel = DEBUG
      """
    )
  )

  private def onEmptyState: UnpersistentBehavior.EventSourced[Command, Event, User] =
    UnpersistentBehavior.fromEventSourced(Handlers())

  "Create a new User" should "persist" in {
    onEmptyState { (testkit, eventProbe, snapshotProbe) =>
      val id = ULID.newULID
      val email = s"test-$id@tester.nl"
      val v = testkit.runAskWithStatus(Command.CreateUser(id, name, email, born, password, _)).receiveStatusReply().getValue
      println(s"v: $v")
      v.name shouldEqual name
      eventProbe.expectPersisted(Event.UserCreated(id, name, email, password, born))
      snapshotProbe.hasEffects shouldBe false
    }
  }

  "Update a new User" should "persist updated information" in {
    onEmptyState { (testkit, eventProbe, snapshotProbe) =>
      val id = ULID.newULID
      val email = s"test-$id@tester.nl"
      val v = testkit.runAskWithStatus(Command.CreateUser(id, name, email, born, password, _)).receiveStatusReply().getValue
      v.name shouldEqual name
      println(s"v: $v")
      eventProbe.expectPersisted(Event.UserCreated(id, name, email, password, born))
      snapshotProbe.hasEffects shouldBe false

      val ou = Repository.findUser(id)
      ou should not be empty
      ou.get.name shouldEqual name

      val updated = testkit.runAskWithStatus(Command.UpdateUser(id, "Breaker", born, password, _)).receiveStatusReply().getValue
      eventProbe.expectPersisted(Event.UserUpdated(id, "Breaker", password, born))
      snapshotProbe.hasEffects shouldBe false
      updated.name shouldEqual "Breaker"
    }
  }

  "Update non existent User" should "return an error" in {
    onEmptyState { (testkit, eventProbe, snapshotProbe) =>
      val id = ULID.newULID
      val updated = testkit.runAskWithStatus(Command.UpdateUser(id, "Breaker", born, password, _)).receiveStatusReply()
      eventProbe.hasEffects shouldBe false
      snapshotProbe.hasEffects shouldBe false
      updated.isError shouldBe true
    }
  }

  "Login" should "return a session token" in {
    onEmptyState { (testkit, eventProbe, snapshotProbe) =>
      val id = ULID.newULID
      val email = s"test-$id@tester.nl"
      testkit.runAskWithStatus(Command.CreateUser(id, name, email, born, password, _)).receiveStatusReply().getValue
      eventProbe.expectPersisted(Event.UserCreated(id, name, email, password, born))
      snapshotProbe.hasEffects shouldBe false

      val token = testkit.runAskWithStatus(Command.Login(email, password, _)).receiveStatusReply().getValue
      eventProbe.expectPersistedType[LoggedIn]()    // can't guess the time
      token should not be null
    }
  }

  "Create and Find" should "return the previously added User" in {
    onEmptyState { (testkit, eventProbe, snapshotProbe) =>
      val id = ULID.newULID
      val email = s"test-$id@tester.nl"
      testkit.runAskWithStatus(Command.CreateUser(id, name, email, born, password, _)).receiveStatusReply().getValue
      eventProbe.expectPersisted(Event.UserCreated(id, name, email, password, born))
      snapshotProbe.hasEffects shouldBe false

      waitForUser()

      Repository.findUser(id) shouldBe Some(User(id, name, email, password, born))
    }
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()
}
