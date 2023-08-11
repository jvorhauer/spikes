package spikes.model

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, Recovery, ReplyEffect, RetentionCriteria}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted, RecoveryFailed, SnapshotSelectionCriteria}
import org.scalactic.TypeCheckedTripleEquals.*
import scalikejdbc.*
import scalikejdbc.interpolation.SQLSyntax.{count, distinct}
import spikes.model.Status.Status
import spikes.model.User.UserId
import spikes.validate.Validation.{ErrorInfo, validate}
import spikes.validate.{bodyRule, dueRule, slugRule, titleRule}
import wvlet.airframe.ulid.ULID

import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt


object Note {

  type NoteId = ULID
  type Reply = StatusReply[ResponseT]
  type ReplyToActor = ActorRef[Reply]

  val key: ServiceKey[Command] = ServiceKey("Note")

  def name(user: UserId, id: NoteId, slug: String): String = s"note-$user-$id-$slug"

  final case class Post(title: String, body: String, due: LocalDateTime, status: Status = Status.New) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set(
      validate(titleRule(title), title, "title"),
      validate(bodyRule(body), body, "body"),
      validate(dueRule(due), due, "due")
    ).flatten
    def asCmd(owner: ULID, replyTo: ActorRef[StatusReply[Note.Response]]): Create = {
      val id = ULID.newULID
      val et = encode(title)
      Note.Create(id, owner, et, encode(body), makeslug(id, et), due, status, replyTo)
    }
  }
  final case class Put(id: ULID, title: String, body: String, slug: String, due: LocalDateTime, status: Status) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set(
      validate(titleRule(title), title, "title"),
      validate(bodyRule(body), body, "body"),
      validate(dueRule(due), due, "due"),
      validate(slugRule(slug), slug, "slug")
    ).flatten
    def asCmd(replyTo: ReplyToActor): Note.Update = Note.Update(id, encode(title), encode(body), slug, due, status, replyTo)
  }
  final case class Delete(id: ULID) extends Request {
    def asCmd(replyTo: ReplyToActor): Remove = Note.Remove(id, replyTo)
  }
  final case class GetById(id: ULID) extends Request {
    def asCmd(replyTo: ReplyToActor): Find = Note.Find(id, replyTo)
  }
  final case class GetBySlug(s: String) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set(
      validate(slugRule(s), s, "slug")
    ).flatten
    def asCmd(replyTo: ReplyToActor): Find = Note.Find(ULID.newULID, replyTo)
  }


  final case class Create(id: ULID, owner: ULID, title: String, body: String, slug: String, due: LocalDateTime, status: Status, replyTo: ActorRef[StatusReply[Note.Response]]) extends Command {
    lazy val asEvent: Created = Created(id, owner, title, body, slug, due, status)
    lazy val asResponse: Response = Response(id, title, body, slug, due, status)
  }
  final case class Update(id: ULID, title: String, body: String, slug: String, due: LocalDateTime, status: Status, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Updated = Updated(id, title, body, due, status)
    lazy val asResponse: Response = Response(id, title, body, slug, due, status)
  }
  final case class Remove(id: ULID, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Removed = Removed(id)
  }
  final case class Find(id: ULID, replyTo: ReplyToActor) extends Command


  sealed trait NoteEvent extends Event

  final case class Created(id: ULID, owner: ULID, title: String, body: String, slug: String, due: LocalDateTime, status: Status) extends NoteEvent
  final case class Updated(id: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends NoteEvent
  final case class Removed(id: ULID) extends NoteEvent

  final case class Response(id: ULID, title: String, body: String, slug: String, due: LocalDateTime, status: Status) extends ResponseT

  final case class State(
      id: NoteId,
      owner: UserId,
      title: String,
      body: String,
      slug: String,
      due: LocalDateTime,
      status: Status
  ) extends Entity {
    lazy val asResponse: Response = Response(id, title, body, slug, due, status)
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
      Status.apply(rs.int("status"))
    )

    def apply(nc: Note.Created) = new State(nc.id, nc.owner, nc.title, nc.body, nc.slug, nc.due, nc.status)
  }

  private def clean(s: String): String = s.trim.toLowerCase.replaceAll("[^ a-z0-9]", "").replaceAll(" ", "-")

  def makeslug(c: LocalDateTime, t: String): String = s"${c.getYear}${c.getMonthValue}${c.getDayOfMonth}-${clean(t)}"
  def makeslug(id: NoteId, t: String): String = makeslug(id.created, t)


  def apply(state: Note.State): Behavior[Command] = Behaviors.setup { ctx =>
    val pid: PersistenceId = PersistenceId("note", state.id.toString(), "-")
    var recovered: Boolean = false

    ctx.system.receptionist.tell(Receptionist.Register(Note.key, ctx.self))

    val commandHandler: (Note.State, Command) => ReplyEffect[Event, Note.State] = (_, cmd) => cmd match {
      case nu: Note.Update => Effect.persist(nu.asEvent).thenReply(nu.replyTo)(state => StatusReply.success(state.asResponse))
      case ng: Note.Find => Effect.none.thenReply(ng.replyTo)(state => StatusReply.success(state.asResponse))
    }

    val eventHandler: (Note.State, Event) => Note.State = (state, evt) => evt match {
      case nu: Note.Updated => state.copy(title = nu.title, body = nu.body, due = nu.due, status = nu.status)
      case _ => state
    }

    EventSourcedBehavior.withEnforcedReplies[Command, Event, Note.State](pid, state, commandHandler, eventHandler)
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      .withRetention(RetentionCriteria.disabled)
      .withRecovery(Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.none))
      .withTagger(_ => Set("note"))
      .receiveSignal {
        case (_, RecoveryCompleted) => recovered = true
        case (_, RecoveryFailed(t)) => ctx.log.error(s"recovery of note $pid failed", t)
      }
  }

  object Repository {

    implicit val session: DBSession = AutoSession

    private val n = Note.State.syntax("n")
    private val cols = Note.State.column
    def save(nc: Note.Created): Note.State = {
      withSQL {
        Note.State.column
        insert.into(Note.State).namedValues(
          cols.id -> nc.id.toString(),
          cols.owner -> nc.owner.toString(),
          cols.title -> nc.title,
          cols.body -> nc.body,
          cols.slug -> nc.slug,
          cols.due -> nc.due,
          cols.status -> nc.status.id
        )
      }.update.apply()
      Note.State(nc)
    }
    def save(nu: Note.Updated): Option[Int] = find(nu.id).map(state => withSQL {
      update(State).set(
        cols.title -> nu.title,
        cols.body -> nu.body,
        cols.due -> nu.due,
        cols.status -> nu.status.id
      ).where.eq(cols.id, state.id.toString())
    }.update.apply()).orElse(None)

    def find(id: ULID): Option[Note.State] = withSQL(select.from(State as n).where.eq(cols.id, id.toString)).map(State(_)).single.apply()
    def find(slug: String): Option[Note.State] = withSQL(select.from(State as n).where.eq(cols.slug, slug)).map(State(_)).single.apply()
    def list(limit: Int = 10, offset: Int = 0): List[Note.State] = withSQL(select.from(State as n).limit(limit).offset(offset)).map(State(_)).list.apply()
    def list(owner: ULID): List[Note.State] = withSQL(select.from(State as n).where.eq(cols.owner, owner.toString)).map(State(_)).list.apply()
    def size(): Int = withSQL(select(distinct(count(cols.id))).from(State as n)).map(_.int(1)).single.apply().getOrElse(0)

    def remove(id: ULID): Boolean = withSQL(delete.from(Note.State).where.eq(cols.id, id.toString)).update.apply() === 1
    def removeAll(): Unit = withSQL(delete.from(Note.State)).update.apply()
  }
}
