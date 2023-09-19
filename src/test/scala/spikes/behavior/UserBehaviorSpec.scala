package spikes.behavior

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import scalikejdbc.DBSession
import spikes.model.{Access, Command, Event, Note, OAuthToken, RichTSID, Session, Status, User, hash, next, now, today}
import spikes.{Spikes, SpikesConfig}


class UserBehaviorSpec extends ScalaTestWithActorTestKit(SpikesConfig.config) with AnyWordSpecLike with Matchers with BeforeAndAfterEach {

  implicit val session: DBSession = Spikes.init

  private val uc = User.Created(next, "Test", "test@miruvor.nl", hash("Welkom123!"), today.minusYears(23), None)
  private val user: User = User.save(uc)
  private val esbtkUser = EventSourcedBehaviorTestKit[Command, Event, User](system, User(user))

  "A User" should {

    "have been created" in {
      val r1 = User.find(user.id)
      r1 should not be empty
    }

    "be updatable" in {
      val res1 = esbtkUser.runCommand[StatusReply[User.Response]](
        replyTo => User.Update(user.id, Some("Updated"), Some("NotWelkom123!"), Some(today.minusYears(32)), Some("bio"), replyTo)
      )
      res1.reply.isSuccess should be (true)
      res1.reply.getValue should ===(
        User.Response(user.id, "Updated", "test@miruvor.nl", user.id.created, today.minusYears(32), Some("bio"))
      )

      val r3 = User.find(user.id)
      r3 should not be empty
      r3.get.id should be (user.id)
      r3.get.name should be ("Updated")
      r3.get.born should be (today.minusYears(32))
      r3.get.bio should be (Some("bio"))

      val r4 = esbtkUser.runCommand[StatusReply[User.Response]](
        replyTo => User.Update(user.id, Some(user.name), Some(hash("Welkom123!")), Some(user.born), user.bio, replyTo)
      )
      r4.reply.isSuccess should be (true)
      val r5 = r4.reply.getValue
      r5.name should be (user.name)

      val res2 = esbtkUser.runCommand[StatusReply[User.Response]](
        replyTo => User.Update(user.id, Some("Vlad"), None, None, None, replyTo)
      )
      res2.reply.isSuccess should be (true)
      val r6 = res2.reply.getValue
      r6.name should be ("Vlad")
      r6.email should be (user.email)
      r6.born should be (user.born)
      r6.bio should be (user.bio)
    }

    "login and get a token" in {

      val res2 = esbtkUser.runCommand[StatusReply[OAuthToken]](
        replyTo => User.Login(user.email, hash("Welkom123!"), replyTo)
      )
      res2.reply.isSuccess should be(true)
      res2.reply.getValue.id should ===(user.id)
      val token = res2.reply.getValue.access_token

      val res3 = Session.find(token)
      res3.isDefined should be (true)
      res3.get.id should ===(user.id)
      res3.get.token should ===(token)

      val res4 = esbtkUser.runCommand[StatusReply[Any]](
        replyTo => User.Logout(token, replyTo)
      )
      res4.reply.isSuccess should be (true)

      val res5 = Session.find(token)
      res5.isDefined should be(false)
    }

    "add a Note" in {
      val res1 = esbtkUser.runCommand[StatusReply[Note.Response]](
        replyTo => Note.Post("my first test title", "test body", now.plusDays(5)).asCmd(user.id, replyTo)
      )
      res1.reply.isSuccess should be (true)
      res1.reply.getValue.title should be ("my first test title")
      res1.reply.getValue.slug should endWith("my-first-test-title")
      val id = res1.reply.getValue.id

      val ns = Note.find(id)
      ns should not be empty

      val nso = Note.find(id, uc.id)
      nso should not be empty

      val nr = esbtkUser.runCommand[StatusReply[Note.Response]](
        replyTo => Note.Delete(id).asCmd(uc.id, replyTo)
      )
      nr.reply.isSuccess should be (true)
    }

    "not add a Note twice" in {
      val noteId = next
      val title = "my first test title"
      val res1 = esbtkUser.runCommand[StatusReply[Note.Response]](
        replyTo => Note.Create(
          noteId, user.id, title, "test body", Note.makeslug(noteId, title), now.plusDays(5), Status.ToDo, Access.Public, replyTo
        )
      )
      res1.reply.isSuccess should be(true)

      val res2 = esbtkUser.runCommand[StatusReply[Note.Response]](
        replyTo => Note.Post(title, "second test body", now.plusDays(6)).asCmd(user.id, replyTo)
      )
      res2.reply.isSuccess should be(false)

      val res3 = Note.find(noteId)
      res3 should not be empty
    }
  }

  override protected def beforeEach(): Unit = {
    esbtkUser.clear()
    Note.list(Int.MaxValue).foreach(_.remove())
    if (User.find(uc.id).isEmpty) {
      User.save(uc)
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
    Note.list(Int.MaxValue).foreach(_.remove())
    User.list(Int.MaxValue).foreach(_.remove())
  }
}
