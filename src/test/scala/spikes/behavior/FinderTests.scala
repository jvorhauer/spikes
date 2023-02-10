package spikes.behavior

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorRef
import com.typesafe.config.ConfigFactory
import spikes.SpikesTest
import spikes.model.Event.UserCreated
import spikes.model.{Event, User}
import wvlet.airframe.ulid.ULID

import java.time.LocalDate
import java.util.UUID

class FinderTests extends SpikesTest {

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

  val finder: ActorRef[Event] = testKit.spawn(Finder(), "finder")

  "Finder" should "find" in {
    val uc = UserCreated(ULID.newULID, "Test", "test@tester.de", "welkom123!", LocalDate.now())
    finder ! uc

    waitForUser()

    Finder.findUser(uc.id) shouldBe Some(User(uc.id, uc.name, uc.email, uc.password, uc.born, Seq.empty))
  }
}
