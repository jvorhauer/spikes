package spikes.model

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply
import org.owasp.encoder.Encode
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import wvlet.airframe.ulid.ULID

class BookmarkTransformerTests extends AnyFlatSpec with Matchers with ScalatestRouteTest {

  private val testkit = ActorTestKit()
  private val probe = testkit.createTestProbe[StatusReply[Bookmark.Response]]().ref

  "All Post transformations" should "pass on data without corruption" in {
    val owner = ULID.newULID
    val post = Bookmark.Post("https://www.miruvor.nl/index.php", "### == \\! This is a test titlé! ***", "The body can be small enough as long as the validation rule for body and title are the same")
    testkit.system.log.info(s"bm1: $post")
    post.validated should have size 0

    val command = post.asCmd(owner, probe)
    command.owner should be (owner)
    command.title should be (Encode.forHtml(post.title))
    command.body should be (Encode.forHtml(post.body))
    command.url should be (Encode.forUriComponent(post.url))

    val event = command.asEvent
    event.title should be (command.title)
    event.body should be (command.body)
    event.id should be (command.id)
    event.owner should be (command.owner)
    event.url should be (command.url)

    val entity = event.asBookmark
    entity.url should be (command.url)
    entity.url should be (event.url)
    entity.id should be (event.id)
    entity.owner should be (event.owner)
    entity.title should be (event.title)

    val response = entity.asResponse
    response.url should be (entity.url)
    response.id should be (entity.id)
    response.title should be (entity.title)
    response.body should be (entity.body)

    response.url should be (command.url)
  }
}
