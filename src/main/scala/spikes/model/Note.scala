package spikes.model

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, Recovery, ReplyEffect, RetentionCriteria}
import akka.persistence.typed.{PersistenceId, RecoveryFailed, SnapshotSelectionCriteria}
import io.scalaland.chimney.dsl.TransformerOps
import org.scalactic.TypeCheckedTripleEquals.*
import scalikejdbc.*
import scalikejdbc.interpolation.SQLSyntax.{count, distinct}
import spikes.model.Access.Access
import spikes.model.Status.Status
import spikes.model.User.UserId
import spikes.validate.Validation.{ErrorInfo, validate}
import wvlet.airframe.ulid.ULID

import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt


object Note {

  type NoteId = ULID
  type Reply = StatusReply[Note.Response]
  type ReplyToActor = ActorRef[Reply]

  val key: ServiceKey[Command] = ServiceKey("Note")

  def name(user: UserId, id: NoteId, slug: String): String = s"note-$user-$id-$slug"

  final case class Post(title: String, body: String, due: LocalDateTime, status: Status = Status.New, access: Access = Access.Public) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set(validate("title", title), validate("body", body), validate("due", due)).flatten
    def asCmd(owner: ULID, replyTo: ActorRef[StatusReply[Note.Response]]): Create = {
      val id = ULID.newULID
      val et = encode(title)
      Note.Create(id, owner, et, encode(body), makeslug(id, et), due, status, access, replyTo)
    }
  }
  final case class Put(id: NoteId, owner: UserId, title: String, body: String, due: LocalDateTime, status: Status, access: Access) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set(validate("title", title), validate("body", body), validate("due", due)).flatten
    def asCmd(replyTo: ReplyToActor): Note.Update = {
      val et = encode(title)
      Note.Update(id, owner, et, encode(body), makeslug(id, et), due, status, access, replyTo)
    }
  }
  final case class Delete(id: ULID) extends Request {
    def asCmd(owner: ULID, replyTo: ActorRef[StatusReply[Note.Response]]): Remove = Note.Remove(id, owner, replyTo)
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
  final case class Remove(id: ULID, owner: ULID, replyTo: ActorRef[StatusReply[Note.Response]]) extends Command {
    def asEvent: Removed = this.transformInto[Removed]
  }


  sealed trait NoteEvent extends Event

  final case class Created(id: ULID, owner: ULID, title: String, body: String, slug: String, due: LocalDateTime, status: Status, access: Access) extends NoteEvent
  final case class Updated(id: ULID, owner: ULID, title: String, body: String, due: LocalDateTime, status: Status, access: Access) extends NoteEvent
  final case class Removed(id: ULID, owner: ULID) extends NoteEvent

  final case class Response(
      id: ULID, title: String, body: String, slug: String, due: LocalDateTime, status: Status, access: Access, comments: List[Comment.Response] = List.empty
  ) extends ResponseT

  final case class State(
      id: NoteId,
      owner: UserId,
      title: String,
      body: String,
      slug: String,
      due: LocalDateTime,
      status: Status,
      access: Access
  ) extends Entity {
    lazy val asResponse: Response = Response(id, title, body, slug, due, status, access, Comment.Repository.onNote(id).map(_.asResponse))
  }
  object State extends SQLSyntaxSupport[State] {
    override val tableName = "notes"
    def apply(rs: WrappedResultSet) = new State(
      ULID(rs.string("id")),
      ULID(rs.string("owner")),
      rs.string("title"),
      rs.string("body"),
      rs.string("slug"),
      rs.localDateTime("due"),
      Status.apply(rs.int("status")),
      Access.apply(rs.int("access"))
    )

    def apply(nc: Note.Created) = new State(nc.id, nc.owner, nc.title, nc.body, nc.slug, nc.due, nc.status, nc.access)
  }

  private def clean(s: String): String = s.trim.toLowerCase.replaceAll("[^ a-z0-9]", "").replaceAll(" ", "-")

  def makeslug(ldt: LocalDateTime, t: String): String = s"${DTF.format(ldt)}-${clean(t)}"
  def makeslug(id: NoteId, t: String): String = makeslug(id.created, t)


  def apply(state: Note.State): Behavior[Command] = Behaviors.setup { ctx =>
    val pid: PersistenceId = PersistenceId("note", state.id.toString())

    ctx.system.receptionist.tell(Receptionist.Register(Note.key, ctx.self))

    val commandHandler: (Note.State, Command) => ReplyEffect[Event, Note.State] = (_, cmd) => cmd match {
      case nu: Note.Update => Effect.persist(nu.asEvent).thenReply(nu.replyTo)(state => StatusReply.success(state.asResponse))
      case nr: Note.Remove => Effect.persist(nr.asEvent).thenReply(nr.replyTo)(state => StatusReply.success(state.asResponse))

      case cc: Comment.Create => if (User.Repository.find(cc.writer).isDefined && Note.Repository.find(cc.noteId).isDefined) {
        Effect.persist(cc.toEvent).thenReply(cc.replyTo)(_ => StatusReply.success(state.asResponse))
      } else
        Effect.reply(cc.replyTo)(StatusReply.error("context for comment not complete"))
    }

    val eventHandler: (Note.State, Event) => Note.State = (state, evt) => evt match {
      case nu: Note.Updated =>
        Note.Repository.save(nu)
        state.copy(title = nu.title, body = nu.body, due = nu.due, status = nu.status, access = nu.access)
      case nr: Note.Removed =>
        Note.Repository.remove(nr.id)
        ctx.system.receptionist.tell(Receptionist.Deregister(Note.key, ctx.self))
        state
      case cc: Comment.Created =>
        Comment.Repository.save(cc)
        state
      case _ => state
    }

    EventSourcedBehavior.withEnforcedReplies[Command, Event, Note.State](pid, state, commandHandler, eventHandler)
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      .withRetention(RetentionCriteria.disabled)
      .withRecovery(Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.none))
      .withTagger(_ => Set("note"))
      .receiveSignal {
        case (_, RecoveryFailed(t)) => ctx.log.error(s"recovery of note $pid failed", t)
      }
  }

  object Repository {

    implicit val session: DBSession = AutoSession

    private val n = Note.State.syntax("n")
    private val cols = Note.State.column
    def save(nc: Note.Created): Note.State = {
      withSQL {
        insert.into(Note.State).namedValues(
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
      Note.State(nc)
    }
    def save(nu: Note.Updated): Option[Int] = find(nu.id).map(state => withSQL {
      update(State).set(
        cols.title -> nu.title,
        cols.body -> nu.body,
        cols.due -> nu.due,
        cols.status -> nu.status.id,
        cols.access -> nu.access.id
      ).where.eq(cols.id, state.id)
    }.update.apply()).orElse(None)

    def find(id: ULID): Option[Note.State] = withSQL(select.from(State as n).where.eq(cols.id, id)).map(State(_)).single.apply()
    def find(slug: String): Option[Note.State] = withSQL(select.from(State as n).where.eq(cols.slug, slug)).map(State(_)).single.apply()
    def find(id: ULID, owner: ULID): Option[Note.State] = withSQL(select.from(State as n).where.eq(cols.id, id).and.eq(cols.owner, owner.toString)).map(State(_)).single.apply()

    def list(limit: Int = 10, offset: Int = 0): List[Note.State] = withSQL(select.from(State as n).limit(limit).offset(offset)).map(State(_)).list.apply()
    def list(owner: UserId): List[Note.State] = withSQL(select.from(State as n).where.eq(cols.owner, owner)).map(State(_)).list.apply()
    def size(): Int = withSQL(select(distinct(count(cols.id))).from(State as n)).map(_.int(1)).single.apply().getOrElse(0)

    def remove(id: NoteId): Boolean = withSQL(delete.from(Note.State).where.eq(cols.id, id)).update.apply() === 1
    def removeAll(): Unit = withSQL(delete.from(Note.State)).update.apply()
  }

  val ddl: Seq[SQLExecution] = Seq(
    sql"""create table if not exists notes (
      id char(26) not null primary key,
      owner char(26) not null,
      title varchar(255) not null,
      body varchar(1024) not null,
      slug varchar(255) not null,
      due timestamp,
      status int,
      access int
    )""".execute,
    sql"create index if not exists notes_owner_idx on notes (owner)".execute,
    sql"create unique index if not exists notes_slug_idx on notes (slug)".execute
  )
}
