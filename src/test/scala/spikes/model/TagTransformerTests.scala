package spikes.model

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class TagTransformerTests extends AnyWordSpecLike with Matchers with ScalatestRouteTest {

  private val testkit = ActorTestKit()
  private val probe = testkit.createTestProbe[StatusReply[ResponseT]]().ref

  "A Tag" should {
    "transform from Post to Create" in {
      val tp = Tag.Post("test tag")
      tp.validated should be (empty)

      val cmd = tp.toCmd(probe.ref)
      cmd.title should be ("test tag")
      cmd.color should be ("000000")

      val evt = cmd.toEvent
      evt.id should be (cmd.id)
      evt.title should be (tp.title)
      evt.color should be (tp.color)
    }
  }
}
