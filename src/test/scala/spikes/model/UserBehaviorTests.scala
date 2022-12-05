package spikes.model

import akka.persistence.testkit.scaladsl.UnpersistentBehavior
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.{Command, Event}

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

class UserBehaviorTests extends AnyFlatSpec with Matchers {

  private val id = UUID.randomUUID()
  private val name = "Tester"
  private val email = "Test@test.er"
  private val born = LocalDate.now().minusYears(21)
  private val password = "Welkom123!"

  private def onEmptyState: UnpersistentBehavior.EventSourced[Command, Event, User] =
    UnpersistentBehavior.fromEventSourced(UserBehavior())

  "Create a new User" should "persist" in {
    onEmptyState { (testkit, eventProbe, snapshotProbe) =>
      val reply = testkit.runAskWithStatus(CreateUser(id, name, email, born, password, _)).receiveStatusReply().getValue
      eventProbe.expectPersisted(UserCreated(id, name, email, password, reply.joined, born))
      snapshotProbe.hasEffects shouldBe false
    }
  }
}
