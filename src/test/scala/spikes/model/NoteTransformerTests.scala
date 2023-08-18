package spikes.model

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import wvlet.airframe.ulid.ULID

class NoteTransformerTests extends AnyFlatSpec with Matchers with ScalatestRouteTest {

  private val testkit = ActorTestKit()
  private val probe = testkit.createTestProbe[StatusReply[ResponseT]]().ref

  "Note.Post" should "transform into Note.Command" in {
    val req = Note.Post("title", "body", now, Status.Blank)
    val cmd = req.asCmd(next, probe)
    cmd.title should be ("title")
    cmd.body should be ("body")
    cmd.due should be (req.due)
    cmd.status should be (Status.Blank)
  }

  "Note.Command" should "transform into Note.Created" in {
    val id  = ULID.newULID
    val cmd = Note.Create(id, next, "title", "body", "slug", now, Status.Completed, Access.Public, probe)
    val evt = cmd.asEvent
    evt.id should be (id)
    evt.title should be ("title")
    evt.body should be ("body")
    evt.due should be (cmd.due)
    evt.status should be (Status.Completed)
  }
}
