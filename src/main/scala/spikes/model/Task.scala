package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.scalaland.chimney.dsl.TransformerOps
import spikes.model.Status.Status
import spikes.validate.Rules
import wvlet.airframe.ulid.ULID

import java.time.LocalDateTime

object Task {

  type ReplyToActor = ActorRef[StatusReply[Task.Response]]

  case class Post(title: String, body: String, due: LocalDateTime, status: Status = Status.New) extends Request {
    val rules = Rules.task
    def asCmd(owner: ULID, replyTo: ActorRef[StatusReply[Task.Response]]): Create = Task.Create(ULID.newULID, owner, title, body, due, status, replyTo)
  }
  case class Put(id: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Request {
    val rules = Rules.task
    def asCmd(owner: ULID, replyTo: ReplyToActor): Task.Update = this.into[Task.Update]
      .withFieldComputed(_.replyTo, _ => replyTo)
      .withFieldComputed(_.owner, _ => owner)
      .transform
  }
  case class Delete(id: ULID) extends Request {
    def asCmd(owner: ULID, replyTo: ReplyToActor): Remove = Task.Remove(id, owner, replyTo)
  }

  case class Create(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Task.Created = this.into[Task.Created].transform
    lazy val asResponse: Task.Response = this.into[Task.Response].transform
  }
  case class Update(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Task.Updated = this.into[Task.Updated].transform
    lazy val asResponse: Response = this.into[Task.Response].transform
  }
  case class Remove(id: ULID, owner: ULID, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Task.Removed = this.into[Task.Removed].transform
  }

  case class Created(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Event {
    lazy val asTask: Task = this.into[Task].transform
  }
  case class Updated(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Event {
    lazy val asTask: Task = this.into[Task].transform
  }
  case class Removed(id: ULID, owner: ULID) extends Event

  case class Response(id: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Respons
}


case class Task(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Entity {
  lazy val asResponse: Task.Response = this.into[Task.Response].transform
}
