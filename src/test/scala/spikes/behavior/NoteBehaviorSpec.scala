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
import spikes.{Spikes, SpikesConfig}


class NoteBehaviorSpec extends ScalaTestWithActorTestKit(SpikesConfig.config) with AnyWordSpecLike with Matchers with BeforeAndAfterEach {

  implicit private val session: DBSession = Spikes.init

  private val uc = User.Created(next, "Test", "test@miruvor.nl", hash("Welkom123!"), today.minusYears(23), None)
  private val user: User = User.save(uc)
  private val esbtkUser = EventSourcedBehaviorTestKit[Command, Event, User](system, User(user))
  private val noteId: NoteId = next

  "A User" should {
    "be able to create a Note" in {
      val r1 = esbtkUser.runCommand[StatusReply[Note.Response]](
        Note.Create(noteId, user.id, "test-title", "test-body", "test-slug", now.plusDays(7), Status.Doing, Access.Public, _)
      )
      r1.reply.isSuccess should be (true)

      var onote = Note.find(noteId)
      onote should not be empty
      var note = onote.get
      note.title should be ("test-title")
      note.id should be (noteId)

      val esbtkNote = EventSourcedBehaviorTestKit[Command, Event, Note](system, Note(note))
      val r2 = esbtkNote.runCommand[StatusReply[Note.Response]](
        Note.Update(noteId, user.id, "updated", "updated body", "updated-slug", note.due, note.status, note.access, _)
      )
      r2.reply.isSuccess should be (true)

      onote = Note.find(noteId)
      onote should not be empty
      note = onote.get
      note.title should be ("updated")

      val r3 = esbtkNote.runCommand[StatusReply[Note.Response]](
        Comment.Create(next, user.id, note.id, None, "test comment", "test body for comment", None, 5, _)
      )
      r3.reply.isSuccess should be (true)
      val nr = r3.reply.getValue
      nr.comments should have size 1
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    Note.list(Int.MaxValue).foreach(_.remove())
    User.list(Int.MaxValue).foreach(_.remove())
    session.close()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    Note.list(Int.MaxValue).foreach(_.remove())
    esbtkUser.clear()
    if (User.find(uc.id).isEmpty) {
      User.save(uc)
    }
  }
}
