package spikes.model

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import wvlet.airframe.ulid.ULID

class NoteTransformerTests extends AnyFlatSpec with Matchers with ScalatestRouteTest {

  private val testkit = ActorTestKit()
  private val probe = testkit.createTestProbe[StatusReply[Note.Response]]().ref

  "Note.Post" should "transform into Note.Command" in {
    val req = Note.Post("title", "body", now, Status.Blank)
    val id  = ULID.newULID
    val cmd = req.asCmd(id, probe)
    cmd.owner should be (id)
    cmd.title should be ("title")
    cmd.body should be ("body")
    cmd.due should be (req.due)
    cmd.status should be (Status.Blank)
  }

  "Note.Command" should "transform into Note.Created" in {
    val id  = ULID.newULID
    val owner = ULID.newULID
    val cmd = Note.Create(id, owner, "title", "body", now, Status.Completed, probe)
    val evt = cmd.asEvent
    evt.id should be (id)
    evt.owner should be (owner)
    evt.title should be ("title")
    evt.body should be ("body")
    evt.due should be (cmd.due)
    evt.status should be (Status.Completed)
  }

  "Note.Event" should "transform into Note" in {
    val id = ULID.newULID
    val owner = ULID.newULID
    val evt = Note.Created(id, owner, "title", "body", now, Status.Doing)
    val ent = evt.asNote
    ent.id should be (id)
    ent.owner should be (owner)
    ent.title should be ("title")
    ent.body should be ("body")
    ent.due should be (evt.due)
    ent.status should be (Status.Doing)
  }

  "Note" should "transform into Note.Response" in {
    val id = ULID.newULID
    val owner = ULID.newULID
    val note = Note(id, owner, "title", "body", now, Status.Doing)
    val resp = note.asResponse
    resp.id should be (id)
    resp.title should be ("title")
    resp.body should be ("body")
    resp.due should be (note.due)
    resp.status should be (Status.Doing)
  }

  "Note.Post" should "transform into Note.Response" in {
    val req = Note.Post("title", "body", now, Status.Blank)
    val id = ULID.newULID
    val cmd = req.asCmd(id, probe)
    val evt = cmd.asEvent
    val note = evt.asNote
    val resp = note.asResponse
    resp.title should be("title")
    resp.body should be("body")
    resp.due should be(note.due)
    resp.status should be(Status.Blank)
  }
}
