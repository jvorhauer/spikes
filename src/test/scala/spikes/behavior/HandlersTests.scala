package spikes.behavior

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.pattern.StatusReply
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import spikes.SpikesTest
import spikes.model._
import spikes.route.InfoRouter

import java.time.LocalDate
import java.util.UUID
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef

class HandlersTests extends SpikesTest with BeforeAndAfterAll {

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
  val probe: TestProbe[StatusReply[User.Response]] = testKit.createTestProbe[StatusReply[User.Response]]("probe")
  val handlers: ActorRef[Command] = testKit.spawn(Handlers(), "handlers-test")

  "Create a new User" should "persist" in {
    val id = next
    val req = User.Post(s"test-$id", s"test-$id@miruvor.nl", password, born)
    handlers ! req.asCmd(probe.ref)
    val res = probe.receiveMessage()
    res.isSuccess should be (true)
    res.getValue.name should be (s"test-$id")
  }

  "Update a new User" should "persist updated information" in {
    val id = next
    handlers ! User.Create(id, s"test-$id", s"test-$id@miruvor.nl", password, born, probe.ref)
    val res = probe.receiveMessage()
    res.isSuccess should be(true)
    res.getValue.name should be(s"test-$id")

    handlers ! User.Update(res.getValue.id, "updated", "Welkom124!", born.minusDays(1), probe.ref)
    val updated = probe.receiveMessage()
    updated.isSuccess should be (true)
    updated.getValue.name should be ("updated")
  }

  "Update non existent User" should "return an error" in {
    val id = next
    handlers ! User.Update(id, s"test-$id", password, born, probe.ref)
    val updated = probe.receiveMessage()
    updated.isSuccess should be(false)
  }

  "Login" should "return a session token" in {
    val id = next
    handlers ! User.Create(id, s"test-$id", s"test-$id@miruvor.nl", password, born, probe.ref)
    val res = probe.receiveMessage()
    res.isSuccess should be(true)
    res.getValue.name should be(s"test-$id")

    val loginProbe = testKit.createTestProbe[StatusReply[OAuthToken]]()
    handlers ! User.Login(s"test-$id@miruvor.nl", password, loginProbe.ref)
    val loggedin = loginProbe.receiveMessage()
    loggedin.isSuccess should be(true)
    loggedin.getValue.access_token should not be null
  }

  "Create and Find" should "return the previously added User" in {
    val id = next
    handlers ! User.Create(id, s"test-$id", s"test-$id@miruvor.nl", password, born, probe.ref)
    val res = probe.receiveMessage()
    res.isSuccess should be (true)
    res.getValue.name should be(s"test-$id")

    handlers ! User.Find(id, probe.ref)
    val found = probe.receiveMessage()
    found.isSuccess should be (true)
    found.getValue.name should be (s"test-$id")
  }

  "Asking for Info" should "return Info" in {
    val prb = testKit.createTestProbe[StatusReply[InfoRouter.Info]]()
    handlers ! InfoRouter.GetInfo(prb.ref)
    val res = prb.receiveMessage()
    res.isSuccess should be (true)
  }


  override def afterAll(): Unit = testKit.shutdownTestKit()
}
