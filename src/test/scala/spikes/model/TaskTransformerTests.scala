package spikes.model

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import wvlet.airframe.ulid.ULID

class TaskTransformerTests extends AnyFlatSpec with Matchers with ScalatestRouteTest {

  private val testkit = ActorTestKit()
  private val probe = testkit.createTestProbe[StatusReply[Task.Response]]().ref

  "Task.Post" should "transform into Task.Command" in {
    val req = Task.Post("title", "body", now, Status.Blank)
    val id  = ULID.newULID
    val cmd = req.asCmd(id, probe)
    cmd.owner should be (id)
    cmd.title should be ("title")
    cmd.body should be ("body")
    cmd.due should be (req.due)
    cmd.status should be (Status.Blank)
  }

  "Task.Command" should "transform into Task.Created" in {
    val id  = ULID.newULID
    val owner = ULID.newULID
    val cmd = Task.Create(id, owner, "title", "body", now, Status.Completed, probe)
    val evt = cmd.asEvent
    evt.id should be (id)
    evt.owner should be (owner)
    evt.title should be ("title")
    evt.body should be ("body")
    evt.due should be (cmd.due)
    evt.status should be (Status.Completed)
  }

  "Task.Event" should "transform into Task" in {
    val id = ULID.newULID
    val owner = ULID.newULID
    val evt = Task.Created(id, owner, "title", "body", now, Status.Doing)
    val ent = evt.asTask
    ent.id should be (id)
    ent.owner should be (owner)
    ent.title should be ("title")
    ent.body should be ("body")
    ent.due should be (evt.due)
    ent.status should be (Status.Doing)
  }

  "Task" should "transform into Task.Response" in {
    val id = ULID.newULID
    val owner = ULID.newULID
    val task = Task(id, owner, "title", "body", now, Status.Doing)
    val resp = task.asResponse
    resp.id should be (id)
    resp.title should be ("title")
    resp.body should be ("body")
    resp.due should be (task.due)
    resp.status should be (Status.Doing)
  }

  "Task.Post" should "transform into Task.Response" in {
    val req = Task.Post("title", "body", now, Status.Blank)
    val id = ULID.newULID
    val cmd = req.asCmd(id, probe)
    val evt = cmd.asEvent
    val task = evt.asTask
    val resp = task.asResponse
    resp.title should be("title")
    resp.body should be("body")
    resp.due should be(task.due)
    resp.status should be(Status.Blank)
  }
}
