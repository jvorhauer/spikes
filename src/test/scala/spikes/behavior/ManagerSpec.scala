package spikes.behavior

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import spikes.model.{Command, Event, RichULID, User, next}

import java.time.LocalDate
import com.typesafe.config.Config

class ManagerSpec extends ScalaTestWithActorTestKit(ManagerSpec.config) with AnyWordSpecLike with BeforeAndAfterEach {

  private val eventSourcedTestKit = EventSourcedBehaviorTestKit[Command, Event, Manager.State](system, Manager())

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "The Manager" should {
    "add a User" in {
      val id = next
      val res1 = eventSourcedTestKit.runCommand[StatusReply[User.Response]](
        replyTo => User.Create(id, "Test", "test@miruvor.nl", "Welkom123!", LocalDate.now().minusYears(21), None, replyTo)
      )
      res1.reply.isSuccess should be (true)
      res1.reply.getValue should ===(
        User.Response(id, "Test", "test@miruvor.nl", id.created, LocalDate.now().minusYears(21), None, Vector.empty)
      )

      val res2 = eventSourcedTestKit.runCommand[StatusReply[Manager.Info]](
        replyTo => Manager.GetInfo(replyTo)
      )
      res2.reply.isSuccess should be(true)
      res2.reply.getValue should ===(
        Manager.Info(1, recovered = true)
      )
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
        Manager.Info(0, recovered = true)
      )

    }
  }
}


object ManagerSpec {
  val config: Config = ConfigFactory.parseString(
    """
      |akka {
      |  actor {
      |    serializers {
      |      kryo = "io.altoo.akka.serialization.kryo.KryoSerializer"
      |    }
      |    serialization-bindings {
      |        "spikes.model.package$SpikeSerializable" = kryo
      |    }
      |  }
      |}""".stripMargin)
    .withFallback(EventSourcedBehaviorTestKit.config)
}
