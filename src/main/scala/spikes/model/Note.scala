package spikes.model

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, Recovery, ReplyEffect, RetentionCriteria}
import akka.persistence.typed.{PersistenceId, RecoveryFailed, SnapshotSelectionCriteria}
import io.hypersistence.tsid.TSID
import io.scalaland.chimney.dsl.*
import org.scalactic.TypeCheckedTripleEquals.*
import scalikejdbc.*
import scalikejdbc.interpolation.SQLSyntax.{count, distinct}
import spikes.model.Access.Access
import spikes.model.Note.NoteId
import spikes.model.Status.Status
import spikes.model.User.UserId
import spikes.validate.*
import spikes.validate.Validator.ValidationError

import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt
import scala.util.matching.Regex


final case class Note(
    id: NoteId, owner: UserId,
    title: String, body: String, slug: String,
    due: LocalDateTime,
    status: Status, access: Access
) extends Entity {
  def toResponse: Note.Response = this.into[Note.Response]
    .withFieldComputed(_.comments, _ => Comment.onNote(id).map(_.toResponse))
    .transform
  def remove(): Unit = Note.remove(id)
}

object Note extends SQLSyntaxSupport[Note] {

  type NoteId = SPID
  type Reply = StatusReply[Note.Response]
  type ReplyToActor = ActorRef[Reply]

  implicit val session: DBSession = AutoSession
  override val tableName = "notes"
  val key: ServiceKey[Command] = ServiceKey("Note")

  def name(user: UserId, id: NoteId, slug: String): String = s"note-$user-$id-$slug"

  def apply(rs: WrappedResultSet) = new Note(
    TSID.from(rs.long("id")),
    TSID.from(rs.long("owner")),
    rs.string("title"),
    rs.string("body"),
    rs.string("slug"),
    rs.localDateTime("due"),
    Status.apply(rs.int("status")),
    Access.apply(rs.int("access"))
  )
  def apply(nc: Note.Created) = new Note(nc.id, nc.owner, nc.title, nc.body, nc.slug, nc.due, nc.status, nc.access)

  private val n = Note.syntax("n")
  private val cols = Note.column
  def save(nc: Note.Created): Note = {
    withSQL {
      insert.into(Note).namedValues(
        cols.id -> nc.id,
        cols.owner -> nc.owner,
        cols.title -> nc.title,
        cols.body -> nc.body,
        cols.slug -> nc.slug,
        cols.due -> nc.due,
        cols.status -> nc.status.id,
        cols.access -> nc.access.id
      )
    }.update.apply()
    Note(nc)
  }
  def save(nu: Note.Updated): Option[Int] = find(nu.id).map(state => withSQL {
    update(Note).set(
      cols.title -> nu.title,
      cols.body -> nu.body,
      cols.due -> nu.due,
      cols.status -> nu.status.id,
      cols.access -> nu.access.id
    ).where.eq(cols.id, state.id)
  }.update.apply()).orElse(None)

  def find(id: NoteId): Option[Note] = withSQL(select.from(Note as n).where.eq(cols.id, id)).map(Note(_)).single.apply()
  def exists(id: NoteId): Boolean = find(id).isDefined
  def find(slug: String): Option[Note] = withSQL(select.from(Note as n).where.eq(cols.slug, slug)).map(Note(_)).single.apply()
  def find(id: NoteId, owner: UserId): Option[Note] = withSQL(select.from(Note as n).where.eq(cols.id, id).and.eq(cols.owner, owner)).map(Note(_)).single.apply()

  def list(limit: Int = 10, offset: Int = 0): List[Note] = withSQL(select.from(Note as n).limit(limit).offset(offset)).map(Note(_)).list.apply()
  def list(owner: UserId): List[Note] = withSQL(select.from(Note as n).where.eq(cols.owner, owner)).map(Note(_)).list.apply()
  def size: Int = withSQL(select(distinct(count(cols.id))).from(Note as n)).map(_.int(1)).single.apply().getOrElse(0)

  def remove(id: NoteId): Boolean = withSQL(delete.from(Note).where.eq(cols.id, id)).update.apply() === 1


  final case class Post(title: String, body: String, due: LocalDateTime, status: Status = Status.New, access: Access = Access.Public) extends Request {
    override def validated: Validated[ValidationError, Note.Post] = Validator(this)
      .satisfying(_.title.matches(Note.Validation.title), Note.Validation.Title(value = this.title))
      .satisfying(_.body.matches(Note.Validation.body), Note.Validation.Body(value = this.body))
      .satisfying(_.due.isAfter(LocalDateTime.now()), Note.Validation.Due(value = this.due.toString))
      .applied
    def asCmd(owner: UserId, replyTo: ActorRef[StatusReply[Note.Response]]): Create = {
      val id = next
      val et = clean(title)
      Note.Create(id, owner, et, clean(body), makeslug(id, et), due, status, access, replyTo)
    }
  }
  final case class Put(id: NoteId, owner: UserId, title: String, body: String, due: LocalDateTime, status: Status, access: Access) extends Request {
    override def validated: Validated[ValidationError, Note.Put] = Validator(this)
      .satisfying(_.title.matches(Note.Validation.title), Note.Validation.Title(value = this.title))
      .satisfying(_.body.matches(Note.Validation.body), Note.Validation.Body(value = this.body))
      .satisfying(_.due.isAfter(LocalDateTime.now()), Note.Validation.Due(value = this.due.toString))
      .applied
    def asCmd(replyTo: ReplyToActor): Note.Update = {
      val et = clean(title)
      Note.Update(id, owner, et, clean(body), makeslug(id, et), due, status, access, replyTo)
    }
  }
  final case class Delete(id: NoteId) extends Request {
    def asCmd(owner: UserId, replyTo: ActorRef[StatusReply[Note.Response]]): Remove = Note.Remove(id, owner, replyTo)
  }

  object Validation {
    val title: Regex = "^[\\p{L}\\s\\d\\W]{1,255}$".r
    val body: Regex = "^[\\p{L}\\s\\d\\W]+$".r

    sealed trait NoteValidationError extends ValidationError {
      override def entity = "Note"
    }
    final case class Title(field: String = "title", value: String) extends NoteValidationError
    final case class Body(field: String = "body", value: String) extends NoteValidationError
    final case class Due(field: String = "due", value: String) extends NoteValidationError
  }


  final case class Create(
      id: NoteId, owner: UserId,
      title: String, body: String, slug: String,
      due: LocalDateTime, status: Status, access: Access,
      replyTo: ActorRef[StatusReply[Note.Response]]
  ) extends Command {
    def asEvent: Created = this.transformInto[Created]
    def asResponse: Response = this.into[Response].enableDefaultValues.transform
  }
  final case class Update(
      id: NoteId, owner: UserId,
      title: String, body: String, slug: String,
      due: LocalDateTime, status: Status, access: Access,
      replyTo: ReplyToActor) extends Command {
    def asEvent: Updated = this.transformInto[Updated]
  }
  final case class Remove(id: NoteId, owner: UserId, replyTo: ActorRef[StatusReply[Note.Response]]) extends Command {
    def asEvent: Removed = this.transformInto[Removed]
  }


  sealed trait NoteEvent extends Event

  final case class Created(id: NoteId, owner: UserId, title: String, body: String, slug: String, due: LocalDateTime, status: Status, access: Access) extends NoteEvent {
    def save: Note = Note.save(this)
  }
  final case class Updated(id: NoteId, owner: UserId, title: String, body: String, due: LocalDateTime, status: Status, access: Access) extends NoteEvent {
    def save: Boolean = Note.save(this).isDefined
  }
  final case class Removed(id: NoteId, owner: UserId) extends NoteEvent

  final case class Response(
      id: NoteId, title: String, body: String, slug: String, due: LocalDateTime, status: Status, access: Access, comments: List[Comment.Response] = List.empty
  ) extends ResponseT

  def makeslug(ldt: LocalDateTime, t: String): String = s"${DTF.format(ldt)}-${t.replaceAll("[^ a-z0-9]", "").replaceAll(" ", "-")}"
  def makeslug(id: NoteId, t: String): String = makeslug(id.created, t)


  def apply(state: Note): Behavior[Command] = Behaviors.setup { ctx =>
    val pid: PersistenceId = PersistenceId("note", state.id.toString)

    ctx.system.receptionist.tell(Receptionist.Register(Note.key, ctx.self))

    val commandHandler: (Note, Command) => ReplyEffect[Event, Note] = (_, cmd) => cmd match {
      case nu: Note.Update => Effect.persist(nu.asEvent).thenReply(nu.replyTo)(state => StatusReply.success(state.toResponse))
      case nr: Note.Remove => Effect.persist(nr.asEvent).thenReply(nr.replyTo)(state => StatusReply.success(state.toResponse))

      case cc: Comment.Create => if (User.exists(cc.writer) && Note.exists(cc.noteId)) {
        Effect.persist(cc.toEvent).thenReply(cc.replyTo)(_ => StatusReply.success(state.toResponse))
      } else
        Effect.reply(cc.replyTo)(StatusReply.error("context for comment not complete"))
      case cu: Comment.Update => Comment.find(cu.id) match {
        case Some(_) => Effect.persist(cu.toEvent).thenReply(cu.replyTo)(note => StatusReply.success(note.toResponse))
        case None => Effect.reply(cu.replyTo)(StatusReply.error(s"comment with id ${cu.id} not found"))
      }
    }

    val eventHandler: (Note, Event) => Note = (state, evt) => evt match {
      case nu: Note.Updated =>
        nu.save
        state.copy(title = nu.title, body = nu.body, due = nu.due, status = nu.status, access = nu.access)
      case nr: Note.Removed =>
        Note.remove(nr.id)
        ctx.system.receptionist.tell(Receptionist.Deregister(Note.key, ctx.self))
        state

      case cc: Comment.Created =>
        cc.save
        state
      case cu: Comment.Updated =>
        cu.save
        state
    }

    EventSourcedBehavior.withEnforcedReplies[Command, Event, Note](pid, state, commandHandler, eventHandler)
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      .withRetention(RetentionCriteria.disabled)
      .withRecovery(Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.none))
      .withTagger(_ => Set("note"))
      .receiveSignal {
        case (_, RecoveryFailed(t)) => ctx.log.error(s"recovery of note $pid failed", t)
      }
  }

  val ddl: Seq[SQLExecution] = Seq(
    sql"""create table if not exists notes (
      id bigint primary key,
      owner bigint not null,
      title varchar(255) not null,
      body varchar(1024) not null,
      slug varchar(255) unique not null,
      due timestamp,
      status int,
      access int
    )""".execute,
    sql"create index if not exists notes_owner_idx on notes (owner)".execute,
    sql"create unique index if not exists notes_slug_idx on notes (slug)".execute
  )
}
