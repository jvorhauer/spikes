package spikes.behavior

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.persistence.testkit.scaladsl.UnpersistentBehavior
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.model.Command.FindUserById
import spikes.model.Event.LoggedIn
import spikes.model._
import wvlet.airframe.ulid.ULID

import java.time.LocalDate
import java.util.UUID

class HandlersTests extends AnyFlatSpec with Matchers {

  private val id = ULID.newULID
  private val name = "Tester"
  private val email = "Test@test.er"
  private val born = LocalDate.now().minusYears(21)
  private val password = "Welkom123!"

  val testKit = ActorTestKit(
    ConfigFactory.parseString(
      s"""akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
          akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
          akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID()}"
          akka.loggers = ["akka.event.Logging$$DefaultLogger"]
          akka.loglevel = DEBUG
      """
    )
  )

  val finder = testKit.spawn(Finder(), "finder")
  private def onEmptyState: UnpersistentBehavior.EventSourced[Command, Event, User] =
    UnpersistentBehavior.fromEventSourced(Handlers(findr = finder))

  "Create a new User" should "persist" in {
    onEmptyState { (testkit, eventProbe, snapshotProbe) =>
      testkit.runAskWithStatus(Command.CreateUser(id, name, email, born, password, _)).receiveStatusReply().getValue
      eventProbe.expectPersisted(Event.UserCreated(id, name, email, password, born))
      snapshotProbe.hasEffects shouldBe false
    }
  }

  "Update a new User" should "persist updated information" in {
    onEmptyState { (testkit, eventProbe, snapshotProbe) =>
      testkit.runAskWithStatus(Command.CreateUser(id, name, email, born, password, _)).receiveStatusReply().getValue
      eventProbe.expectPersisted(Event.UserCreated(id, name, email, password, born))
      snapshotProbe.hasEffects shouldBe false

      val updated = testkit.runAskWithStatus(Command.UpdateUser(id, "Breaker", born, password, _)).receiveStatusReply().getValue
      eventProbe.expectPersisted(Event.UserUpdated(id, "Breaker", password, born))
      snapshotProbe.hasEffects shouldBe false
      updated.name shouldEqual "Breaker"
    }
  }

  "Update non existent User" should "return an error" in {
    onEmptyState { (testkit, eventProbe, snapshotProbe) =>
      val updated = testkit.runAskWithStatus(Command.UpdateUser(id, "Breaker", born, password, _)).receiveStatusReply()
      eventProbe.hasEffects shouldBe false
      snapshotProbe.hasEffects shouldBe false
      updated.isError shouldBe true
    }
  }

  "Login" should "return a session token" in {
    onEmptyState { (testkit, eventProbe, snapshotProbe) =>
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
      testkit.runAskWithStatus(Command.CreateUser(id, name, email, born, password, _)).receiveStatusReply().getValue
      eventProbe.expectPersisted(Event.UserCreated(id, name, email, password, born))
      snapshotProbe.hasEffects shouldBe false

      val user = testkit.runAskWithStatus(FindUserById(id, _)).receiveStatusReply().getValue
      user.name shouldEqual name
    }
  }
}
