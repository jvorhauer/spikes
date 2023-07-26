package spikes.behavior

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.Scheduler
import akka.pattern.StatusReply
import akka.util.Timeout
import spikes.SpikesTest
import spikes.model.{User, next}

import java.time.LocalDate
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import akka.actor.testkit.typed.scaladsl.TestProbe

class AbuserTests extends SpikesTest {

  implicit val timeout: Timeout = 1.second

  val testKit: ActorTestKit = ActorTestKit("abuser-testkit", cfg)
  val probe: TestProbe[StatusReply[User.Response]] = testKit.createTestProbe[StatusReply[User.Response]]("probe")

  implicit val executionContext: ExecutionContextExecutor = testKit.internalSystem.executionContext
  implicit val scheduler: Scheduler = testKit.internalSystem.scheduler

  "Create and Update a User" should "be verifieable" in {
    val id = next
    val created = User.Created(id, "test", s"test-$id@bla.bla", "Welkom123!", LocalDate.now().minusYears(23), None)
    val abuser = testKit.spawn(User(User.State(created)), s"abuser-${id}")

    abuser ! User.Find(id, probe.ref)
    var found = probe.receiveMessage()
    found.isSuccess should be (true)
    found.getValue.name should be ("test")

    abuser ! User.Update(id, "updated", "Welkom234!", LocalDate.now().minusYears(24), probe.ref)
    val updated = probe.receiveMessage()
    updated.isSuccess should be (true)
    updated.getValue.name should be ("updated")

    abuser ! User.Find(id, probe.ref)
    found = probe.receiveMessage()
    found.isSuccess should be(true)
    found.getValue.name should be("updated")

    abuser ! User.Remove(id, probe.ref)
    val removed = probe.receiveMessage()
    removed.isSuccess should be (true)
    removed.getValue.name should be("updated")
  }
}
