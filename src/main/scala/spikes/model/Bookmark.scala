package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.scalaland.chimney.dsl.TransformerOps
import org.owasp.encoder.Encode
import spikes.model.Bookmark.Response
import spikes.validate.Validation.{FieldErrorInfo, validate}
import spikes.validate.{bodyRule, titleRule}
import wvlet.airframe.ulid.ULID

case class Bookmark(id: ULID, owner: ULID, url: String, title: String, body: String) extends Entity {
  lazy val asResponse: Response = this.into[Response].transform
}

object Bookmark {

  type ReplyToActor = ActorRef[StatusReply[Response]]

  case class Post(url: String, title: String, body: String) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set.apply(
      validate(titleRule(title), title, "title"),
      validate(bodyRule(body), body, "body")
    ).flatten
    lazy val asCmd: (ULID, ReplyToActor) => Create =
      (owner, replyTo) => Create(next, owner, Encode.forUriComponent(url), Encode.forHtml(title), Encode.forHtml(body), replyTo)
  }
  case class Put(id: ULID, owner: ULID, url: String, title: String, body: String, replyTo: ReplyToActor) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set.empty
    lazy val asCmd: ReplyToActor => Update = replyTo => Update(id, owner, url, title, body, replyTo)
  }
  case class Delete(id: ULID) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set.empty
    lazy val asCmd: ReplyToActor => Remove = replyTo => Remove(id, replyTo)
  }

  case class Create(id: ULID, owner: ULID, url: String, title: String, body: String, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Created = this.into[Created].transform
    lazy val asBookmark: Bookmark = this.into[Bookmark].transform
  }
  case class Update(id: ULID, owner: ULID, url: String, title: String, body: String, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Updated = this.into[Updated].transform
  }
  case class Remove(id: ULID, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Removed = this.into[Removed].transform
  }

  case class Created(id: ULID, owner: ULID, url: String, title: String, body: String) extends Event {
    lazy val asBookmark: Bookmark = this.into[Bookmark].transform
  }
  case class Updated(id: ULID, owner: ULID, url: String, title: String, body: String) extends Event {
    lazy val asBookmark: Bookmark = this.into[Bookmark].transform
  }
  case class Removed(id: ULID) extends Event

  case class Response(id: ULID, url: String, title: String, body: String) extends Respons
}
