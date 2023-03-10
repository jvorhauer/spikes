package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.scalaland.chimney.dsl.TransformerOps
import org.owasp.encoder.Encode
import spikes.model.Status.Status
import spikes.validate.Validation.FieldErrorInfo
import wvlet.airframe.ulid.ULID

import java.time.LocalDateTime
import scala.collection.mutable

object Task {

  type ReplyToActor = ActorRef[StatusReply[Task.Response]]

  case class Post(title: String, body: String, due: LocalDateTime, status: Status = Status.New) extends Request {
    lazy val validated: Set[FieldErrorInfo] = {
      val errors: mutable.Set[FieldErrorInfo] = mutable.Set.empty
      if (title.isBlank) errors += FieldErrorInfo("title", "title should not be blank")
      if (body.isBlank) errors += FieldErrorInfo("body", "body should not be blank")
      if (due.isBefore(now)) errors += FieldErrorInfo("due", "due should not be in the past")
      errors.toSet[FieldErrorInfo]
    }
    def asCmd(owner: ULID, replyTo: ActorRef[StatusReply[Task.Response]]): Create = Task.Create(ULID.newULID, owner, Encode.forHtml(title), Encode.forHtml(body), due, status, replyTo)
  }
  case class Put(id: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Request {
    lazy val validated: Set[FieldErrorInfo] = {
      val errors: mutable.Set[FieldErrorInfo] = mutable.Set.empty
      if (title.isBlank) errors += FieldErrorInfo("title", "title should not be blank")
      if (body.isBlank) errors += FieldErrorInfo("body", "body should not be blank")
      if (due.isBefore(now)) errors += FieldErrorInfo("due", "due should not be in the past")
      errors.toSet[FieldErrorInfo]
    }
    def asCmd(owner: ULID, replyTo: ReplyToActor): Task.Update = Task.Update(id, owner, Encode.forHtml(title), Encode.forHtml(body), due, status, replyTo)
  }
  case class Delete(id: ULID) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set.empty
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
