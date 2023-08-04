package spikes.model

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, Recovery, ReplyEffect, RetentionCriteria}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted, RecoveryFailed, SnapshotSelectionCriteria}
import io.scalaland.chimney.dsl.TransformerOps
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
    def asCmd(replyTo: ActorRef[StatusReply[Note.Response]]): Create = {
      val id = ULID.newULID
      val et = encode(title)
      Note.Create(id, et, encode(body), makeslug(id, et), due, status, replyTo)
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

  final case class Create(id: ULID, title: String, body: String, slug: String, due: LocalDateTime, status: Status, replyTo: ActorRef[StatusReply[Note.Response]]) extends Command {
    lazy val asEvent: Note.Created = this.into[Note.Created].transform
    lazy val asResponse: Note.Response = Response(id, title, body, slug, due, status)
  }
  final case class Update(id: ULID, title: String, body: String, slug: String, due: LocalDateTime, status: Status, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Note.Updated = this.into[Note.Updated].transform
    lazy val asResponse: Response = Response(id, title, body, slug, due, status)
  }
  final case class Remove(id: ULID, replyTo: ReplyToActor) extends Command {
    lazy val asEvent: Note.Removed = this.into[Note.Removed].transform
  }
  final case class Find(id: ULID, replyTo: ReplyToActor) extends Command

  final case class Created(id: ULID, title: String, body: String, slug: String, due: LocalDateTime, status: Status) extends Event {
    lazy val asState: Note.State = Note.State(id, title, body, slug, due, status)
  }
  final case class Updated(id: ULID, title: String, body: String, due: LocalDateTime, status: Status) extends Event
  final case class Removed(id: ULID) extends Event

  final case class Response(id: ULID, title: String, body: String, slug: String, due: LocalDateTime, status: Status) extends ResponseT

  final case class State(id: NoteId, title: String, body: String, slug: String, due: LocalDateTime, status: Status) {
    lazy val asResponse: Note.Response = this.into[Note.Response].transform
  }

  private def clean(s: String): String = s.trim.toLowerCase.replaceAll("[^ a-z0-9]", "").replaceAll(" ", "-")
  private def makeslug(c: LocalDateTime, t: String): String = s"${c.getYear}${c.getMonth}${c.getDayOfMonth}-${clean(t)}"
  private def makeslug(id: NoteId, t: String): String = makeslug(id.created, t)


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
}
