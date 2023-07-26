package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import spikes.model.Comment.CommentId
import spikes.model.Note.NoteId
import spikes.model.User.UserId
import spikes.validate.Validation.*
import spikes.validate.{bodyRule, titleRule}
import wvlet.airframe.ulid.ULID


final case class Comment(id: CommentId, title: String, body: String)

object Comment {

  type CommentId = ULID
  type Reply = StatusReply[Comment.Response]
  type ReplyTo = ActorRef[Reply]

  final case class Post(writer: UserId, note: NoteId, title: String, body: String) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set(
      validate(titleRule(title), title, "title"),
      validate(bodyRule(body), body, "body"),
    ).flatten

    def asCmd(replyTo: ReplyTo): Create = Create(ULID.newULID, title, body, replyTo)
  }


  final case class Create(id: CommentId, title: String, body: String, replyTo: ReplyTo) extends Command

  final case class Created(id: CommentId, title: String, body: String) extends Event

  final case class Response(id: CommentId, title: String, body: String) extends Respons
}
