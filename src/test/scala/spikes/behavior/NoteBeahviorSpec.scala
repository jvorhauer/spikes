package spikes.behavior

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import scalikejdbc.DBSession
import spikes.model.Note.NoteId
import spikes.model.{Access, Command, Comment, Event, Note, Status, User, hash, next, now, today}
import spikes.{Spikes, SpikesConfig, model}
import wvlet.airframe.ulid.ULID


class NoteBeahviorSpec extends ScalaTestWithActorTestKit(SpikesConfig.config) with AnyWordSpecLike with Matchers with BeforeAndAfterEach {

  implicit val session: DBSession = Spikes.init

  private val uc = User.Created(next, "Test", "test@miruvor.nl", hash("Welkom123!"), today.minusYears(23), None)
  private val user: User.State = User.Repository.save(uc)
  private val esbtkUser = EventSourcedBehaviorTestKit[Command, Event, User.State](system, User(user))
  private val noteId: NoteId = ULID.newULID

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    esbtkUser.clear()
    if (User.Repository.find(uc.id).isEmpty) {
      User.Repository.save(uc)
    }
  }

  "A User" should {
    "be able to create a Note" in {
      val r1 = esbtkUser.runCommand[StatusReply[Note.Response]](
        replyTo => Note.Create(noteId, user.id, "test-title", "test-body", "test-slug", now.plusDays(7), Status.Doing, Access.Public, replyTo)
      )
      r1.reply.isSuccess should be (true)

      var onote = Note.Repository.find(noteId)
      onote should not be empty
      var note = onote.get
      note.title should be ("test-title")
      note.id should be (noteId)

      val esbtkNote = EventSourcedBehaviorTestKit[Command, Event, Note.State](system, Note(onote.get))
      val r2 = esbtkNote.runCommand[StatusReply[Note.Response]](
        replyTo => Note.Update(noteId, user.id, "updated", "updated body", "updated-slug", note.due, note.status, note.access, replyTo)
      )
      r2.reply.isSuccess should be (true)

      onote = Note.Repository.find(noteId)
      onote should not be empty
      note = onote.get
      note.title should be ("updated")

      println(s"user: ${User.Repository.find(uc.id)}")
      println(s"note: ${Note.Repository.find(noteId)}")

      val r3 = esbtkNote.runCommand[StatusReply[Note.Response]](
        replyTo => Comment.Create(next, user.id, note.id, "test comment", "test body for comment", None, 5, replyTo)
      )
      println(s"r3: ${r3.reply}")
      r3.reply.isSuccess should be (true)
      val nr = r3.reply.getValue
      nr.comments should have size 1
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    model.Note.Repository.removeAll()
    User.Repository.nuke()
  }
}
