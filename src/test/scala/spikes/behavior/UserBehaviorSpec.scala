package spikes.behavior

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import scalikejdbc.DBSession
import spikes.model.{Command, Event, Note, OAuthToken, Status, User, hash, next}
import spikes.{Spikes, SpikesConfig}

import java.time.{LocalDate, LocalDateTime}

class UserBehaviorSpec extends ScalaTestWithActorTestKit(SpikesConfig.config) with AnyWordSpecLike with Matchers with BeforeAndAfterEach {

  implicit val session: DBSession = Spikes.init

  private val uc = User.Created(next, "Test", "test@miruvor.nl", hash("Welkom123!"), LocalDate.now().minusYears(23), None)
  private val user: User.State = User.Repository.save(uc)
  private val eventSourcedTestKit = EventSourcedBehaviorTestKit[Command, Event, User.State](system, User(user))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "A User" should {

    "have been created" in {
      val r1 = User.Repository.find(user.id)
      r1 should not be empty
    }

    "be updatable" in {
      val res1 = eventSourcedTestKit.runCommand[StatusReply[User.Response]](
        replyTo => User.Update(user.id, "Updated", "NotWelkom123!", LocalDate.now().minusYears(32), Some("bio"), replyTo)
      )
      res1.reply.isSuccess should be (true)
      res1.reply.getValue should ===(
        User.Response(user.id, "Updated", "test@miruvor.nl", user.joined, LocalDate.now().minusYears(32), Some("bio"))
      )

      val r3 = User.Repository.find(user.id)
      r3 should not be empty
      r3.get.id should be (user.id)
      r3.get.name should be ("Updated")

      val r4 = eventSourcedTestKit.runCommand[StatusReply[User.Response]](
        replyTo => User.Update(user.id, user.name, hash("Welkom123!"), user.born, user.bio, replyTo)
      )
      r4.reply.isSuccess should be (true)
    }

    "login and get a token" in {

      val res2 = eventSourcedTestKit.runCommand[StatusReply[OAuthToken]](
        replyTo => User.Login(user.email, hash("Welkom123!"), replyTo)
      )
      res2.reply.isSuccess should be(true)
      res2.reply.getValue.id should ===(user.id)
      val token = res2.reply.getValue.access_token

      val res3 = eventSourcedTestKit.runCommand[Option[User.Session]](
        replyTo => User.Authorize(token, replyTo)
      )
      res3.reply.isDefined should be (true)
      res3.reply.get.id should ===(user.id)
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
        replyTo => Note.Post("my first test title", "test body", LocalDateTime.now().plusDays(5)).asCmd(user.id, replyTo)
      )
      res1.reply.isSuccess should be (true)
      res1.reply.getValue.title should be ("my first test title")
      res1.reply.getValue.slug should endWith("my-first-test-title")
      val id = res1.reply.getValue.id

      val ns = Note.Repository.find(id)
      ns should not be empty
    }

    "not add a Note twice" in {

      Note.Repository.removeAll()

      val noteId = next
      val title = "my first test title"
      val res1 = eventSourcedTestKit.runCommand[StatusReply[Note.Response]](
        replyTo => Note.Create(noteId, user.id, title, "test body", Note.makeslug(noteId, title), LocalDateTime.now().plusDays(5), Status.ToDo, replyTo)
      )
      res1.reply.isSuccess should be(true)

      val res2 = eventSourcedTestKit.runCommand[StatusReply[Note.Response]](
        replyTo => Note.Post(title, "second test body", LocalDateTime.now().plusDays(6)).asCmd(user.id, replyTo)
      )
      res2.reply.isSuccess should be(false)

      val res3 = Note.Repository.find(noteId)
      res3 should not be empty
    }
  }
}
