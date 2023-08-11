package spikes.behavior

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import scalikejdbc.DBSession
import spikes.model.{Command, Event, RichULID, User, next}
import spikes.{Spikes, SpikesConfig}

import java.time.LocalDate

class ManagerSpec extends ScalaTestWithActorTestKit(SpikesConfig.config) with AnyWordSpecLike with BeforeAndAfterEach {

  implicit val session: DBSession = Spikes.init

  private val eventSourcedTestKit = EventSourcedBehaviorTestKit[Command, Event, Manager.State](system, Manager())

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
    User.Repository.nuke()
  }

  "The Manager" should {
    "add a User" in {
      val id = next
      val res1 = eventSourcedTestKit.runCommand[StatusReply[User.Response]](
        replyTo => User.Create(id, "Test", "test@miruvor.nl", "Welkom123!", LocalDate.now().minusYears(21), None, replyTo)
      )
      res1.reply.isSuccess should be (true)
      res1.reply.getValue should ===(
        User.Response(id, "Test", "test@miruvor.nl", id.created, LocalDate.now().minusYears(21), None)
      )

      val res2 = eventSourcedTestKit.runCommand[StatusReply[Manager.Info]](
        replyTo => Manager.GetInfo(replyTo)
      )
      res2.reply.isSuccess should be(true)
      res2.reply.getValue should ===(
        Manager.Info(1, 0, recovered = true)
      )

      val r3 = User.Repository.find(id)
      r3 should not be empty

      User.Repository.size() should be (1)
    }

    "reject already added user" in {
      val id = next
      val res1 = eventSourcedTestKit.runCommand[StatusReply[User.Response]](
        replyTo => User.Create(id, "Test", "test@miruvor.nl", "Welkom123!", LocalDate.now().minusYears(21), None, replyTo)
      )
      res1.reply.isSuccess should be(true)
      val res2 = eventSourcedTestKit.runCommand[StatusReply[User.Response]](
        replyTo => User.Create(id, "Other", "test@miruvor.nl", "Welkom123!", LocalDate.now().minusYears(32), Some("Hello"), replyTo)
      )
      res2.reply.isSuccess should be(false)
    }

    "delete a user" in {
      val id = next
      val res1 = eventSourcedTestKit.runCommand[StatusReply[User.Response]](
        replyTo => User.Create(id, "Test", "test@miruvor.nl", "Welkom123!", LocalDate.now().minusYears(21), None, replyTo)
      )
      res1.reply.isSuccess should be(true)
      val res2 = eventSourcedTestKit.runCommand[StatusReply[User.Response]](
        replyTo => User.Remove(id, replyTo)
      )
      res2.reply.isSuccess should be(true)
      res2.reply.getValue should ===(
        User.Response(id, "Test", "test@miruvor.nl", id.created, LocalDate.now().minusYears(21))
      )

      val res3 = eventSourcedTestKit.runCommand[StatusReply[Manager.Info]](
        replyTo => Manager.GetInfo(replyTo)
      )
      res3.reply.isSuccess should be(true)
      res3.reply.getValue should ===(
        Manager.Info(0, 0, recovered = true)
      )
    }
  }
}
