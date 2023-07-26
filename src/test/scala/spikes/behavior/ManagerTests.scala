package spikes.behavior

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import spikes.SpikesTest
import spikes.model.{Command, User, next}

import java.time.LocalDate
import scala.concurrent.ExecutionContext

class ManagerTests extends SpikesTest {

  val testKit: ActorTestKit = ActorTestKit("manager-testkit", cfg)
  val probe: TestProbe[StatusReply[User.Response]] = testKit.createTestProbe[StatusReply[User.Response]]("probe")
  val probe2: TestProbe[StatusReply[Manager.Info]] = testKit.createTestProbe[StatusReply[Manager.Info]]("probe2")
  val manager: ActorRef[Command] = testKit.spawn(Manager(), "manager-test")

  implicit val ec: ExecutionContext = testKit.internalSystem.executionContext

  "Create a new User, then Update, then Remove" should "end up with no Users" in {
    val getinfo = Manager.GetInfo(probe2.ref)

    manager ! getinfo
    var resi = probe2.receiveMessage()
    resi.isSuccess should be (true)
    val init = resi.getValue.users

    val id = next
    val create = User.Create(id, "test", s"test-$id@test.nl", "Welkom123!", LocalDate.now().minusYears(21), None, probe.ref)
    manager ! create
    val res = probe.receiveMessage()
    res.isSuccess should be (true)
    res.getValue.name should be ("test")

    manager ! getinfo
    resi = probe2.receiveMessage()
    resi.isSuccess should be(true)
    resi.getValue.users should be(init + 1L)

    // test recovery:
    val man2 = testKit.spawn(Manager(), "recover-test")
    man2 ! getinfo
    resi = probe2.receiveMessage()
    resi.isSuccess should be (true)
    resi.getValue.users should be (init + 1L)
  }

  "Create same User twice" should "return an error" in {
    val id = next
    val create = User.Create(id, "test", s"test-${id}@test.nl", "Welkom123!", LocalDate.now().minusYears(21), None, probe.ref)
    manager ! create
    val res = probe.receiveMessage()
    res.isSuccess should be(true)
    res.getValue.name should be("test")

    val error = create.copy(id = next)
    manager ! error
    val res2 = probe.receiveMessage()
    res2.isSuccess should be (false)
    res2.getError.getMessage should be (s"email ${error.email} already in use")
  }

  "Create a User and Delete that User" should "make that User unavailable" in {

    val getinfo = Manager.GetInfo(probe2.ref)
    manager ! getinfo
    var resi = probe2.receiveMessage()
    resi.isSuccess should be(true)
    val init = resi.getValue.users

    val id = next
    val create = User.Create(id, "deleter", s"test-${id}@test.nl", "Welkom123!", LocalDate.now().minusYears(21), None, probe.ref)
    manager ! create
    val res = probe.receiveMessage()
    res.isSuccess should be (true)
    res.getValue.name should be ("deleter")

    manager ! getinfo
    resi = probe2.receiveMessage()
    resi.isSuccess should be(true)
    resi.getValue.users should be(init + 1L)

    val remove = User.Remove(id, probe.ref)
    manager ! remove
    val res2 = probe.receiveMessage()
    res2.isSuccess should be (true)

    manager ! getinfo
    resi = probe2.receiveMessage()
    resi.isSuccess should be(true)
    resi.getValue.users should be(init)
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }
}
