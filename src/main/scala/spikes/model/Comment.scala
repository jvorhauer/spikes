package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.hypersistence.tsid.TSID
import io.scalaland.chimney.dsl.*
import org.scalactic.TypeCheckedTripleEquals.*
import scalikejdbc.*
import spikes.model.Comment.CommentId
import spikes.model.Comment.Validation.CommentValidationError
import spikes.model.Note.NoteId
import spikes.model.User.UserId
import spikes.validate.Validator.ValidationError
import spikes.validate.{Validated, Validator}

import scala.util.matching.Regex


final case class Comment(
    id: CommentId,
    writer: UserId,
    noteId: NoteId,
    parent: Option[CommentId] = None,
    title: String,
    body: String,
    color: Option[String],
    stars: Int = 0
) extends Entity {
  def toResponse: Comment.Response = this.transformInto[Comment.Response]
}

object Comment extends SQLSyntaxSupport[Comment] {

  implicit val session: DBSession = AutoSession
  private val c = Comment.syntax("c")
  private val cols = Comment.column

  override val tableName = "comments"

  type CommentId = SPID
  type Reply = StatusReply[Note.Response]
  type ReplyTo = ActorRef[Reply]

  def apply(cc: Comment.Created) = new Comment(cc.id, cc.writer, cc.noteId, cc.parent, cc.title, cc.body, cc.color, cc.stars)
  def apply(rs: WrappedResultSet) = new Comment(
    TSID.from(rs.long("id")),
    TSID.from(rs.long("writer")),
    TSID.from(rs.long("note_id")),
    rs.longOpt("parent").map(TSID.from),
    rs.string("title"),
    rs.string("body"),
    rs.stringOpt("color"),
    rs.int("stars")
  )

  def save(cc: Comment.Created): Comment = {
    withSQL {
      insert.into(Comment).namedValues(
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
    Comment(cc)
  }
  def save(cu: Comment.Updated): Option[Comment] = find(cu.id).map { state =>
    withSQL {
      update(Comment).set(
        cols.title -> cu.title,
        cols.body -> cu.body,
        cols.color -> cu.color,
        cols.stars -> cu.stars
      ).where.eq(cols.id, state.id)
    }.update.apply()
    Comment(state.id, state.writer, state.noteId, state.parent, cu.title, cu.body, cu.color, cu.stars)
  }

  def find(id: CommentId): Option[Comment] = withSQL(select.from(Comment as c).where.eq(cols.id, id)).map(Comment(_)).single.apply()
  def onNote(noteId: NoteId): List[Comment] = withSQL(select.from(Comment as c).where.eq(cols.noteId, noteId)).map(Comment(_)).list.apply()
  def byWriter(userId: UserId): List[Comment] = withSQL(select.from(Comment as c).where.eq(cols.writer, userId)).map(Comment(_)).list.apply()
  def remove(id: CommentId): Boolean = withSQL(delete.from(Comment).where.eq(cols.id, id)).update.apply() === 1


  final case class Post(noteId: NoteId, title: String, body: String, color: Option[String] = None, stars: Int = 0, parent: Option[CommentId] = None) extends Request {
    override def validated: Validated[CommentValidationError, Comment.Post] = Validator(this)
      .satisfying(_.title.matches(Validation.title), Validation.Title(value = this.title))
      .satisfying(_.body.matches(Validation.body), Validation.Body(value = this.body))
      .satisfying(post => post.color.isEmpty || post.color.get.matches(Validation.color), Validation.Color(value = this.color.getOrElse("???")))
      .satisfying(post => post.stars >= 0 && post.stars < 6, Validation.Stars(value = this.stars.toString))
      .applied
    def asCmd(writer: UserId, replyTo: ReplyTo): Create = Create(next, writer, noteId, parent, clean(title), clean(body), color, stars, replyTo)
  }
  final case class Put(id: CommentId, title: String, body: String, color: Option[String] = None, stars: Int = 0) extends Request {
    override def validated: Validated[CommentValidationError, Comment.Put] = Validator(this)
      .satisfying(_.title.matches(Validation.title), Validation.Title(value = this.title))
      .satisfying(_.body.matches(Validation.body), Validation.Body(value = this.body))
      .satisfying(post => post.color.isEmpty || post.color.get.matches(Validation.color), Validation.Color(value = this.color.getOrElse("???")))
      .satisfying(post => post.stars >= 0 && post.stars < 6, Validation.Stars(value = this.stars.toString))
      .applied
    def asCmd(replyTo: ReplyTo): Update = Update(id, clean(title), clean(body), color, stars, replyTo)
  }
  final case class Delete(id: CommentId) extends Request {
    def asCmd(replyTo: ReplyTo): Remove = this.into[Remove].withFieldComputed(_.replyTo, _ => replyTo).transform
  }

  object Validation {
    val title: Regex = "^[\\p{L}\\s\\d\\W]{1,255}$".r
    val body: Regex = "^[\\p{L}\\s\\d\\W]+$".r
    val color: Regex = "^[0-9A-Fa-f]{6}$".r

    sealed trait CommentValidationError extends ValidationError {
      override def entity: String = "Comment"
    }
    final case class Title(field: String = "title", value: String) extends CommentValidationError
    final case class Body(field: String = "body", value: String) extends CommentValidationError
    final case class Color(field: String = "color", value: String) extends CommentValidationError
    final case class Stars(field: String = "stars", value: String, override val error: String = "must be between 0 and 5 inclusive") extends CommentValidationError
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
    def toState: Comment = this.into[Comment].transform
    def save: Comment = Comment.save(this)
  }
  final case class Updated(id: CommentId, title: String, body: String, color: Option[String], stars: Int) extends Event {
    def save: Boolean = Comment.save(this).isDefined
  }
  final case class Removed(id: CommentId) extends Event

  final case class Response(
      id: CommentId, writer: UserId, noteId: NoteId, parent: Option[CommentId], title: String, body: String, color: Option[String], stars: Int = 0
  ) extends ResponseT


  val ddl: Seq[SQLExecution] = Seq(
    sql"""create table if not exists comments (
         id bigint primary key,
         writer bigint not null,
         note_id bigint not null,
         parent bigint,
         title varchar(255) not null,
         body varchar(1024) not null,
         color varchar(6),
         stars tinyint not null
    )""".execute,
    sql"create index if not exists comments_note_idx on comments (note_id)".execute,
    sql"create index if not exists comments_writer_idx on comments (writer)".execute
  )
}
