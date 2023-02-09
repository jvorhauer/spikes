package spikes.behavior

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorRef
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.model.Event.UserCreated
import spikes.model.{Event, User}
import wvlet.airframe.ulid.ULID

import java.time.LocalDate
import java.util.UUID

class FinderTests extends AnyFlatSpec with Matchers {

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
    Thread.sleep(5) // wait for async call to complete, ask is probably nicer to use, but overkill here
    Finder.findUser(uc.id) shouldBe Some(User(uc.id, uc.name, uc.email, uc.password, uc.born, Seq.empty))
  }
}
