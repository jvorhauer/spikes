package spikes.behavior

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import spikes.model.{Command, Event, Note, OAuthToken, User, hash, next}

import java.time.{LocalDate, LocalDateTime}

class UserSpec extends ScalaTestWithActorTestKit(ManagerSpec.config) with AnyWordSpecLike with BeforeAndAfterEach {

  private val state: User.State = User.State(next, "Test", "test@miruvor.nl", hash("Welkom123!"), LocalDate.now().minusYears(23))
  private val eventSourcedTestKit = EventSourcedBehaviorTestKit[Command, Event, User.State](system, User(state))
  private val today = LocalDate.now()
  private val prefix = s"${today.getYear}${today.getMonth}${today.getDayOfMonth}"

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "A User" should {
    "be updatable" in {
      val res1 = eventSourcedTestKit.runCommand[StatusReply[User.Response]](
        replyTo => User.Update(state.id, "Updated", "NotWelkom123!", LocalDate.now().minusYears(32), Some("bio"), replyTo)
      )
      res1.reply.isSuccess should be (true)
      res1.reply.getValue should ===(
        User.Response(state.id, "Updated", "test@miruvor.nl", state.joined, LocalDate.now().minusYears(32), Some("bio"))
      )

      val res2 = eventSourcedTestKit.runCommand[StatusReply[User.Response]](User.Find(state.id, _))
      res2.reply.isSuccess should be (true)
      res2.reply.getValue should ===(
        User.Response(state.id, "Updated", "test@miruvor.nl", state.joined, LocalDate.now().minusYears(32), Some("bio"))
      )
    }

    "login and get a token" in {
      val res2 = eventSourcedTestKit.runCommand[StatusReply[OAuthToken]](
        replyTo => User.Login(state.email, "Welkom123!", replyTo)
      )
      res2.reply.isSuccess should be(true)
      res2.reply.getValue.id should ===(state.id)
      val token = res2.reply.getValue.access_token

      val res3 = eventSourcedTestKit.runCommand[Option[User.Session]](
        replyTo => User.Authorize(token, replyTo)
      )
      res3.reply.isDefined should be (true)
      res3.reply.get.id should ===(state.id)
      res3.reply.get.token should ===(token)

      val res4 = eventSourcedTestKit.runCommand[StatusReply[Any]](
        replyTo => User.Logout(token, replyTo)
      )
      res4.reply.isSuccess should be (true)

      val res5 = eventSourcedTestKit.runCommand[Option[User.Session]](
        replyTo => User.Authorize(token, replyTo)
      )
      res5.reply.isDefined should be(false)
    }

    "add a Note" in {
      val res1 = eventSourcedTestKit.runCommand[StatusReply[Note.Response]](
        replyTo => Note.Post("my first test title", "test body", LocalDateTime.now().plusDays(5)).asCmd(replyTo)
      )
      res1.reply.isSuccess should be (true)
      res1.reply.getValue.title should be ("my first test title")
      res1.reply.getValue.slug should endWith("my-first-test-title")

      val res2 = eventSourcedTestKit.runCommand[StatusReply[User.Response]](User.Find(state.id, _))
      res2.reply.isSuccess should be(true)
      res2.reply.getValue should ===(
        User.Response(
          state.id, "Test", "test@miruvor.nl", state.joined, LocalDate.now().minusYears(23), None, Vector(s"$prefix-my-first-test-title")
        )
      )
    }

    "not add a Note twice" in {
      val res1 = eventSourcedTestKit.runCommand[StatusReply[Note.Response]](
        replyTo => Note.Post("my first test title", "test body", LocalDateTime.now().plusDays(5)).asCmd(replyTo)
      )
      res1.reply.isSuccess should be(true)

      val res2 = eventSourcedTestKit.runCommand[StatusReply[Note.Response]](
        replyTo => Note.Post("my first test title", "second test body", LocalDateTime.now().plusDays(6)).asCmd(replyTo)
      )
      res2.reply.isSuccess should be(false)

      val res3 = eventSourcedTestKit.runCommand[StatusReply[User.Response]](User.Find(state.id, _))
      res3.reply.isSuccess should be(true)
      res3.reply.getValue should ===(
        User.Response(
          state.id, "Test", "test@miruvor.nl", state.joined, LocalDate.now().minusYears(23), None, Vector(s"$prefix-my-first-test-title")
        )
      )
    }
  }
}

object UserSpec {
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
