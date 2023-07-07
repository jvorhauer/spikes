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


case class Note(
     id: ULID,
     owner: ULID,
     title: String,
     body: String,
     due: LocalDateTime,
     status: Status,
     @underlying vertex: Option[Vertex] = None
) extends Entity {
  lazy val created: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
  lazy val asResponse: Note.Response = this.into[Note.Response].transform
}


object Note {

  type Reply = StatusReply[Note.Response]
  type ReplyToActor = ActorRef[Reply]


  case class Post(title: String, body: String, due: LocalDateTime, status: Status = Status.New) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set(
      validate(titleRule(title), title, "title"),
      validate(bodyRule(body), body, "body"),
      validate(dueRule(due), due, "due")
    ).flatten
    def asCmd(owner: ULID, replyTo: ReplyToActor): Create = Note.Create(ULID.newULID, owner, Encode.forHtml(title), Encode.forHtml(body), due, status, replyTo)
  }
  case class Put(id: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set(
      validate(titleRule(title), title, "title"),
      validate(bodyRule(body), body, "body"),
      validate(dueRule(due), due, "due")
    ).flatten
    def asCmd(owner: ULID, replyTo: ReplyToActor): Note.Update = Note.Update(id, owner, Encode.forHtml(title), Encode.forHtml(body), due, status, replyTo)
  }
  case class Delete(id: ULID) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set.empty
    def asCmd(replyTo: ReplyToActor): Remove = Note.Remove(id, replyTo)
  }
  case class Get(id: ULID) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set.empty
    def asCmd(replyTo: ReplyToActor): Find = Note.Find(id, replyTo)
  }

  case class Create(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Note.Created = this.into[Note.Created].transform
    lazy val asResponse: Note.Response = this.into[Note.Response].transform
  }
  case class Update(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Note.Updated = this.into[Note.Updated].transform
    lazy val asResponse: Response = this.into[Note.Response].transform
  }
  case class Remove(id: ULID, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Note.Removed = this.into[Note.Removed].transform
  }
  case class Find(id: ULID, replyTo: ReplyToActor) extends Command

  case class Created(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Event {
    lazy val asNote: Note = this.into[Note].withFieldComputed(_.vertex, _ => None).transform
  }
  case class Updated(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Event {
    lazy val asNote: Note = this.into[Note].withFieldComputed(_.vertex, _ => None).transform
  }
  case class Removed(id: ULID) extends Event

  case class Response(id: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Respons
}
