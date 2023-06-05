package spikes.behavior

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import spikes.SpikesTest
import spikes.model.*
import spikes.route.InfoRouter

import java.time.{LocalDate, LocalDateTime}

class HandlersTests extends SpikesTest {

  private val born = LocalDate.now().minusYears(21)
  private val password = "Welkom123!"
  private val bio = Some("It's me!!")

  val testKit: ActorTestKit = ActorTestKit(cfg)
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
    handlers ! User.Create(id, s"test-$id", s"test-$id@miruvor.nl", password, born, bio, probe.ref)
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
    handlers ! User.Create(id, s"test-$id", s"test-$id@miruvor.nl", password, born, bio, probe.ref)
    val res = probe.receiveMessage()
    res.isSuccess should be(true)
    res.getValue.name should be(s"test-$id")

    val loginProbe = testKit.createTestProbe[StatusReply[OAuthToken]]()
    handlers ! User.Login(s"test-$id@miruvor.nl", password, loginProbe.ref)
    val loggedin = loginProbe.receiveMessage()
    loggedin.isSuccess should be(true)
    loggedin.getValue.access_token should not be empty

    handlers ! User.Login("hacker@kremlin.ru", password, loginProbe.ref)
    loginProbe.receiveMessage().isSuccess should be (false)
  }

  "Create and Find" should "return the previously added User" in {
    val id = next
    handlers ! User.Create(id, s"test-$id", s"test-$id@miruvor.nl", password, born, bio, probe.ref)
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

  "A Task" should "be workable" in {
    val id = next
    handlers ! User.Create(id, s"test-task-$id", s"test-$id@miruvor.nl", password, born, bio, probe.ref)
    probe.receiveMessage().isSuccess should be (true)

    val prb = testKit.createTestProbe[StatusReply[Task.Response]]()
    handlers ! Task.Create(next, id, "Test Title", "Test Body", LocalDateTime.now().plusDays(1), Status.ToDo, prb.ref)
    prb.receiveMessage().isSuccess should be (true)

    handlers ! User.Find(id, probe.ref)
    val res1 = probe.receiveMessage()
    res1.isSuccess should be (true)
    res1.getValue.tasks should have size 1
    res1.getValue.tasks.head.title should be ("Test Title")

    handlers ! Task.Update(res1.getValue.tasks.head.id, id, "Updated Title", "Test Body", LocalDateTime.now().plusDays(3), Status.Doing, prb.ref)
    val res2 = prb.receiveMessage()
    res2.isSuccess should be (true)
    res2.getValue.title should be ("Updated Title")

    handlers ! User.Find(id, probe.ref)
    val res3 = probe.receiveMessage()
    res3.isSuccess should be(true)
    res3.getValue.tasks should have size 1
    res3.getValue.tasks.head.title should be("Updated Title")

    handlers ! Task.Remove(res1.getValue.tasks.head.id, prb.ref)
    prb.receiveMessage().isSuccess should be (true)

    handlers ! User.Find(id, probe.ref)
    val res4 = probe.receiveMessage()
    res4.isSuccess should be(true)
    res4.getValue.tasks should have size 0
  }

  "A Bookmark" should "CRUD" in {
    val id = next
    handlers ! User.Create(id, s"test-task-$id", s"test-$id@miruvor.nl", password, born, bio, probe.ref)
    probe.receiveMessage().isSuccess should be (true)

    val prb = testKit.createTestProbe[StatusReply[Bookmark.Response]]()
    handlers ! Bookmark.Create(next, id, "http://localhost:8080/users", "Test Title", "Test Body", prb.ref)
    prb.receiveMessage().isSuccess should be (true)

    handlers ! User.Find(id, probe.ref)
    val res1 = probe.receiveMessage()
    res1.isSuccess should be (true)
    res1.getValue.bookmarks should have size 1
    res1.getValue.bookmarks.head.title should be ("Test Title")

    handlers ! Bookmark.Update(res1.getValue.bookmarks.head.id, id, "http://updated:9090/bookmarks", "Updated Title", "Updated Body", prb.ref)
    prb.receiveMessage().isSuccess should be (true)

    handlers ! User.Find(id, probe.ref)
    val res2 = probe.receiveMessage()
    res2.isSuccess should be (true)
    res2.getValue.bookmarks should have size 1
    res2.getValue.bookmarks.head.title should be ("Updated Title")

    handlers ! Bookmark.Remove(res1.getValue.bookmarks.head.id, prb.ref)
    prb.receiveMessage().isSuccess should be (true)

    handlers ! User.Find(id, probe.ref)
    val res3 = probe.receiveMessage()
    res3.isSuccess should be (true)
    res3.getValue.bookmarks should have size 0

  }


  override def afterAll(): Unit = testKit.shutdownTestKit()
}
