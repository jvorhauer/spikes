package spikes.model

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class CommentTransformerSpec extends AnyWordSpecLike with Matchers with ScalatestRouteTest {

  private val testkit = ActorTestKit()
  private val probe = testkit.createTestProbe[StatusReply[ResponseT]]().ref

  "Comment Transformations" should {
    "Post to Command to Event to State to Response" in {
      val cpo = Comment.Post(next, "title", "body", Some("00FF00"), 4)
      cpo.validated should be (empty)

      val ccc = cpo.asCmd(next, probe)
      ccc.title should be("title")
      ccc.body should be("body")
      ccc.noteId should be(cpo.noteId)
      ccc.replyTo.path should be (probe.path)

      val ecc = ccc.toEvent
      ecc.title should be ("title")
      ecc.body should be ("body")
      ecc.noteId should be (cpo.noteId)
      ecc.color should be (Some("00FF00"))
      ecc.stars should be (4)

      val cs = ecc.toState
      cs.title should be ("title")
      cs.body should be ("body")
      cs.noteId should be (cpo.noteId)
      cs.color should be (cpo.color)
      cs.stars should be (cpo.stars)
    }

    "Put to Command to Event" in {
      val cpu = Comment.Put(next, "title", "body", Some("FF00FF"), 2)
      cpu.validated should be (empty)

      val cuc = cpu.asCmd(probe)
      cuc.title should be ("title")
      cuc.body should be ("body")
      cuc.id should be (cpu.id)
      cuc.color should be (cpu.color)
      cuc.stars should be (cpu.stars)
      cuc.replyTo.path should be (probe.path)

      val cue = cuc.toEvent
      cue.id should be (cpu.id)
      cue.title should be (cpu.title)
    }

    "Delete to Command to Event" in {
      val cdc = Comment.Delete(next)
      val crc = cdc.asCmd(probe)
      crc.id should be (cdc.id)
      crc.replyTo.path should be (probe.path)
      val cre = crc.toEvent
      cre.id should be (cdc.id)
    }
  }
}
