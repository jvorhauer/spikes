package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.scalaland.chimney.dsl.TransformerOps
import scalikejdbc.*
import spikes.model
import spikes.model.Comment.CommentId
import spikes.model.Note.NoteId
import spikes.model.User.UserId
import spikes.validate.Validation.*
import wvlet.airframe.ulid.ULID


final case class Comment(id: CommentId, title: String, body: String)

object Comment {

  type CommentId = ULID
  type Reply = StatusReply[Note.Response]
  type ReplyTo = ActorRef[Reply]

  final case class Post(writer: UserId, noteId: NoteId, title: String, body: String, color: Option[String] = None, stars: Int = 0, parent: Option[CommentId] = None) extends Request {
    override def validated: Set[ErrorInfo] = Set(validate("title", title), validate("body", body), validate("color", color), validate("stars", stars)).flatten
    def asCmd(replyTo: ReplyTo): Create = Create(next, writer, noteId, parent, encode(title), encode(body), color, stars, replyTo)
  }
  final case class Put(id: CommentId, title: String, body: String, color: Option[String] = None, stars: Int = 0) extends Request {
    override def validated: Set[ErrorInfo] = Set(validate("title", title), validate("body", body), validate("color", color), validate("stars", stars)).flatten
    def asCmd(replyTo: ReplyTo): Update = Update(id, encode(title), encode(body), color, stars, replyTo)
  }
  final case class Delete(id: CommentId) extends Request {
    def asCmd(replyTo: ReplyTo): Remove = this.into[Remove].withFieldComputed(_.replyTo, _ => replyTo).transform
  }

  final case class Create(
      id: CommentId, writer: UserId, noteId: NoteId, parent: Option[CommentId],
      title: String, body: String, color: Option[String], stars: Int,
      replyTo: ReplyTo
  ) extends Command {
    def toEvent: Comment.Created = this.transformInto[Created]
  }
  final case class Update(id: CommentId, title: String, body: String, color: Option[String], stars: Int, replyTo: ReplyTo) extends Command {
    def toEvent: Comment.Updated = this.transformInto[Updated]
  }
  final case class Remove(id: CommentId, replyTo: ReplyTo) extends Command {
    def toEvent: Removed = this.transformInto[Removed]
  }


  final case class Created(
      id: CommentId, writer: UserId, noteId: NoteId, parent: Option[CommentId], title: String, body: String, color: Option[String], stars: Int = 0
  ) extends Event {
    def toState: Entity = this.into[Comment.Entity].transform
  }
  final case class Updated(id: CommentId, title: String, body: String, color: Option[String], stars: Int) extends Event
  final case class Removed(id: CommentId) extends Event

  final case class Entity(
      id: CommentId, writer: UserId, noteId: NoteId, parent: Option[CommentId] = None, title: String, body: String, color: Option[String], stars: Int = 0
  ) extends model.Entity {
    lazy val asResponse: Response = this.transformInto[Response]
  }
  object Entity extends SQLSyntaxSupport[Entity] {
    override val tableName = "comments"
    def apply(rs: WrappedResultSet) = new Entity(
      ULID(rs.string("id")),
      ULID(rs.string("writer")),
      ULID(rs.string("note_id")),
      rs.stringOpt("parent").map(ULID(_)),
      rs.string("title"),
      rs.string("body"),
      rs.stringOpt("color"),
      rs.int("stars")
    )
    def apply(cc: Comment.Created) = new Entity(cc.id, cc.writer, cc.noteId, cc.parent, cc.title, cc.body, cc.color, cc.stars)
  }

  final case class Response(
      id: CommentId, writer: UserId, noteId: NoteId, parent: Option[CommentId], title: String, body: String, color: Option[String], stars: Int = 0
  ) extends ResponseT


  object Repository {
    implicit val session: DBSession = AutoSession
    private val c = Comment.Entity.syntax("c")
    private val cols = Comment.Entity.column

    def save(cc: Comment.Created): Comment.Entity = {
      withSQL {
        insert.into(Comment.Entity).namedValues(
          cols.id -> cc.id,
          cols.writer -> cc.writer,
          cols.noteId -> cc.noteId,
          cols.parent -> cc.parent,
          cols.title -> cc.title,
          cols.body -> cc.body,
          cols.color -> cc.color,
          cols.stars -> cc.stars
        )
      }.update.apply()
      Comment.Entity(cc)
    }

    def onNote(noteId: NoteId): List[Comment.Entity] = withSQL(select.from(Entity as c).where.eq(cols.noteId, noteId)).map(Comment.Entity(_)).list.apply()
    def byWriter(userId: UserId): List[Comment.Entity] = withSQL(select.from(Entity as c).where.eq(cols.writer, userId)).map(Comment.Entity(_)).list.apply()
  }
}
