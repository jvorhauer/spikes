package spikes.model

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, Recovery, ReplyEffect, RetentionCriteria}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted, RecoveryFailed, SnapshotSelectionCriteria}
import io.scalaland.chimney.dsl.TransformerOps
import org.owasp.encoder.Encode
import spikes.model.Status.Status
import spikes.model.User.UserId
import spikes.validate.Validation.{ErrorInfo, validate}
import spikes.validate.{bodyRule, dueRule, slugRule, titleRule}
import wvlet.airframe.ulid.ULID

import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt


object Note {

  type NoteId = ULID

  type Reply = StatusReply[Respons]
  type ReplyToActor = ActorRef[Reply]

  val key: ServiceKey[Command] = ServiceKey("Note")

  def name(user: UserId, id: NoteId, title: String): String = s"note-$user-$id-${makeslug(user, title)}"

  def apply(state: Note.State): Behavior[Command] = Behaviors.setup { ctx =>
    val pid: PersistenceId = PersistenceId("note", state.id.toString(), "-")
    var recovered: Boolean = false

    ctx.system.receptionist.tell(Receptionist.Register(Note.key, ctx.self))

    val commandHandler: (Note.State, Command) => ReplyEffect[Event, Note.State] = (_, cmd) => cmd match {
      case nu: Note.Update => Effect.persist(nu.asEvent).thenReply(nu.replyTo)(state => StatusReply.success(state.asResponse))
      case ng: Note.Find => Effect.none.thenReply(ng.replyTo)(state => StatusReply.success(state.asResponse))
    }

    val eventHandler: (Note.State, Event) => Note.State = (state, evt) => evt match {
      case nu: Note.Updated => state.copy(body = nu.body, due = nu.due)  // can't update title as this is part of the key to check existence of a Note
      case _ => state
    }

    EventSourcedBehavior.withEnforcedReplies[Command, Event, Note.State](pid, state, commandHandler, eventHandler)
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1)
      )
      .withRetention(RetentionCriteria.disabled)
      .withRecovery(Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.none))
      .receiveSignal {
        case (_, RecoveryCompleted) => recovered = true
        case (_, RecoveryFailed(t)) => ctx.log.error(s"recovery of note $pid failed", t)
      }
  }

  case class Post(title: String, body: String, due: LocalDateTime, status: Status = Status.New) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set(
      validate(titleRule(title), title, "title"),
      validate(bodyRule(body), body, "body"),
      validate(dueRule(due), due, "due")
    ).flatten
    def asCmd(replyTo: Note.ReplyToActor): Create =
      Note.Create(ULID.newULID, Encode.forHtml(title), Encode.forHtml(body), due, status, replyTo)
  }
  case class Put(id: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set(
      validate(titleRule(title), title, "title"),
      validate(bodyRule(body), body, "body"),
      validate(dueRule(due), due, "due")
    ).flatten
    def asCmd(replyTo: ReplyToActor): Note.Update = Note.Update(id, Encode.forHtml(title), Encode.forHtml(body), due, status, replyTo)
  }
  case class Delete(id: ULID) extends Request {
    def asCmd(replyTo: ReplyToActor): Remove = Note.Remove(id, replyTo)
  }
  case class GetById(id: ULID) extends Request {
    def asCmd(replyTo: ReplyToActor): Find = Note.Find(id, replyTo)
  }
  case class GetBySlug(s: String) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set(
      validate(slugRule(s), s, "slug")
    ).flatten
    def asCmd(replyTo: ReplyToActor): Find = Note.Find(ULID.newULID, replyTo)
  }

  case class Create(id: ULID, title: String, body: String, due: LocalDateTime, status: Status, replyTo: Note.ReplyToActor) extends Command {
    lazy val slug: String = makeslug(id.created, title)
    lazy val asEvent: Note.Created = this.into[Note.Created].transform
    lazy val asResponse: Note.Response = Response(id, title, body, due, makeslug(id, title), status)
  }
  case class Update(id: ULID, title: String, body: String, due: LocalDateTime, status: Status, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Note.Updated = this.into[Note.Updated].transform
    lazy val asResponse: Response = Response(id, title, body, due, makeslug(id, title), status)
    lazy val slug: String = makeslug(id, title)
  }
  case class Remove(id: ULID, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Note.Removed = this.into[Note.Removed].transform
  }
  case class Find(id: ULID, replyTo: ReplyToActor) extends Command

  case class Created(id: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Event {
    lazy val slug: String = makeslug(id, title)
    lazy val asState: Note.State = Note.State(id, title, body, due, slug, status)
  }
  case class Updated(id: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Event
  case class Removed(id: ULID) extends Event

  case class Response(id: ULID, title: String, body: String, due: LocalDateTime, slug: String, status: Status) extends Respons

  final case class State(id: NoteId, title: String, body: String, due: LocalDateTime, slug: String, status: Status) {
    lazy val asResponse: Note.Response = this.into[Note.Response].transform
  }

  private def makeslug(c: LocalDateTime, t: String): String = s"${c.getYear}${c.getMonthValue}${c.getDayOfMonth}-${t.replace(' ', '-').toLowerCase}"
  private def makeslug(id: NoteId, t: String): String = makeslug(id.created, t)
}
