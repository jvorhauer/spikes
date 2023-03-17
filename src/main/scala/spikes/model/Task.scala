package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import gremlin.scala.{Vertex, underlying}
import io.scalaland.chimney.dsl.TransformerOps
import org.owasp.encoder.Encode
import spikes.model.Status.Status
import spikes.validate.Validation.{FieldErrorInfo, validate}
import spikes.validate.{titleRule, bodyRule, dueRule}
import wvlet.airframe.ulid.ULID

import java.time.{LocalDateTime, ZoneId}

case class Task(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status, @underlying vertex: Option[Vertex] = None) extends Entity {
  lazy val created: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
  lazy val asResponse: Task.Response = this.into[Task.Response].transform
}

object Task {

  type Reply = StatusReply[Task.Response]
  type ReplyToActor = ActorRef[Reply]

  case class Post(title: String, body: String, due: LocalDateTime, status: Status = Status.New) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set(
      validate(titleRule(title), title, "title"),
      validate(bodyRule(body), body, "body"),
      validate(dueRule(due), due, "due")
    ).flatten
    def asCmd(owner: ULID, replyTo: ReplyToActor): Create = Task.Create(ULID.newULID, owner, Encode.forHtml(title), Encode.forHtml(body), due, status, replyTo)
  }
  case class Put(id: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set(
      validate(titleRule(title), title, "title"),
      validate(bodyRule(body), body, "body"),
      validate(dueRule(due), due, "due")
    ).flatten
    def asCmd(owner: ULID, replyTo: ReplyToActor): Task.Update = Task.Update(id, owner, Encode.forHtml(title), Encode.forHtml(body), due, status, replyTo)
  }
  case class Delete(id: ULID) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set.empty
    def asCmd(replyTo: ReplyToActor): Remove = Task.Remove(id, replyTo)
  }

  case class Create(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Task.Created = this.into[Task.Created].transform
    lazy val asResponse: Task.Response = this.into[Task.Response].transform
  }
  case class Update(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Task.Updated = this.into[Task.Updated].transform
    lazy val asResponse: Response = this.into[Task.Response].transform
  }
  case class Remove(id: ULID, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Task.Removed = this.into[Task.Removed].transform
  }

  case class Created(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Event {
    lazy val asTask: Task = this.into[Task].withFieldComputed(_.vertex, _ => None).transform
  }
  case class Updated(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Event {
    lazy val asTask: Task = this.into[Task].withFieldComputed(_.vertex, _ => None).transform
  }
  case class Removed(id: ULID) extends Event

  case class Response(id: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Respons
}
