package spikes.behavior

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import scalikejdbc.DBSession
import spikes.build.BuildInfo
import spikes.model.{Command, Event, RichTSID, Session, Tag, User, next, now, today}
import spikes.{Spikes, SpikesConfig}


class ManagerSpec extends ScalaTestWithActorTestKit(SpikesConfig.config) with AnyWordSpecLike with BeforeAndAfterEach {

  implicit val session: DBSession = Spikes.init
  private val esbtkManager = EventSourcedBehaviorTestKit[Command, Event, Manager.State](system, Manager())

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    gc()
    esbtkManager.clear()
  }

  "The Manager" should {
    "add a User" in {
      val id = next
      val res1 = esbtkManager.runCommand[StatusReply[User.Response]](
        User.Create(id, "Test", "test@miruvor.nl", "Welkom123!", today.minusYears(21), None, _)
      )
      res1.reply.isSuccess should be (true)
      res1.reply.getValue should ===(
        User.Response(id, "Test", "test@miruvor.nl", id.created, today.minusYears(21), None)
      )

      val res2 = esbtkManager.runCommand[StatusReply[Manager.Info]](Manager.GetInfo)
      res2.reply.isSuccess should be(true)
      res2.reply.getValue should ===(Manager.Info(recovered = true, users = 1, notes = 0, sessions = 0))

      val r3 = User.find(id)
      r3 should not be empty

      User.size should be (1)
    }

    "reject already added user" in {
      val id = next
      val res1 = esbtkManager.runCommand[StatusReply[User.Response]](
        User.Create(id, "Test", "test@miruvor.nl", "Welkom123!", today.minusYears(21), None, _)
      )
      res1.reply.isSuccess should be(true)
      val res2 = esbtkManager.runCommand[StatusReply[User.Response]](
        User.Create(id, "Other", "test@miruvor.nl", "Welkom123!", today.minusYears(32), Some("Hello"), _)
      )
      res2.reply.isSuccess should be(false)
    }

    "delete a user" in {
      val id = next
      val res1 = esbtkManager.runCommand[StatusReply[User.Response]](
        User.Create(id, "Test", "test@miruvor.nl", "Welkom123!", today.minusYears(21), None, _)
      )
      res1.reply.isSuccess should be(true)
      val res2 = esbtkManager.runCommand[StatusReply[Any]](
        replyTo => User.Remove(id, replyTo)
      )
      res2.reply.isSuccess should be(true)

      val res3 = esbtkManager.runCommand[StatusReply[Manager.Info]](Manager.GetInfo)
      res3.reply.isSuccess should be(true)
      val info = res3.reply.getValue
      info.users should be (0)
      info.notes should be (0)
      info.recovered should be (true)
      info.version should be (BuildInfo.version)
    }

    "provide readiness" in {
      val res1 = esbtkManager.runCommand[StatusReply[Boolean]](Manager.IsReady)
      res1.reply.isSuccess should be (true)
      res1.reply.getValue should be (true)
    }

    "CUD a tag" in {

      val start = Tag.size

      val id = next
      val res1 = esbtkManager.runCommand[StatusReply[Tag.Response]](Tag.Create(id, "test title", "FF00FF", _))
      res1.reply.isSuccess should be(true)
      val created = res1.reply.getValue
      created.id should be (id)
      created.title should be ("test title")
      created.color should be ("FF00FF")

      val res2 = esbtkManager.runCommand[StatusReply[Tag.Response]](Tag.Update(id, "other title", "00FF00", _))
      res2.reply.isSuccess should be (true)
      val updated = res2.reply.getValue
      updated.id should be (id)
      updated.title should be ("other title")
      updated.color should be ("00FF00")

      val res3 = esbtkManager.runCommand[StatusReply[Tag.Response]](Tag.Remove(id, _))
      res3.reply.isSuccess should be (true)
      val deleted = res3.reply.getValue
      deleted.id should be (id)
      deleted.title should be ("other title")
      deleted.color should be ("00FF00")

      Tag.size should be (start)
    }

    "Reap Sessions" in {
      val res1 = esbtkManager.runCommand[Command](SessionReaper.Reap)
      res1.reply should be (SessionReaper.Done)

      val user = User(next, "session test name", "test@test.er", "Welkom123!", today.minusYears(33), None)
      Session.save(user, now.minusSeconds(1))
      val res2 = esbtkManager.runCommand[Command](
        replyTo => SessionReaper.Reap(replyTo)
      )
      res2.reply should be (SessionReaper.Done)
      Session.size should be (0)
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
    session.close()
    gc()
  }

  private def gc() = {
    User.list(Int.MaxValue).foreach(_.remove())
    Tag.list.foreach(_.remove())
    Session.list.foreach(_.remove())
  }
}
