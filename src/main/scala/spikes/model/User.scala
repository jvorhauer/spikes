package spikes.model

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, Recovery, ReplyEffect, RetentionCriteria}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted, RecoveryFailed, SnapshotSelectionCriteria}
import io.scalaland.chimney.dsl.TransformerOps
import org.owasp.encoder.Encode
import spikes.validate.Validation.{ErrorInfo, validate}
import spikes.validate.{bornRule, emailRule, nameRule, passwordRule}
import wvlet.airframe.ulid.ULID

import java.time.{LocalDate, LocalDateTime, ZoneId}
import scala.concurrent.duration.DurationInt

object User {

  val key: ServiceKey[Command] = ServiceKey("User")

  type UserId = ULID

  type ReplyTo = ActorRef[StatusReply[Respons]]
  type ReplyListTo = ActorRef[StatusReply[List[Respons]]]
  type ReplyTokenTo = ActorRef[StatusReply[OAuthToken]]
  type ReplySessionTo = ActorRef[Option[User.Session]]
  type ReplyAnyTo = ActorRef[StatusReply[Any]]

  def name(id: UserId, email: String): String = s"user-$id-$email-${hash(id)}"

  final case class Post(name: String, email: String, password: String, born: LocalDate, bio: Option[String] = None) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set(
      validate(nameRule(name), name, "name"),
      validate(emailRule(email), email, "email"),
      validate(passwordRule(password), password, "password"),
      validate(bornRule(born), born, "born")
    ).flatten
    def asCmd(replyTo: ActorRef[StatusReply[User.Response]]): Create = Create(ULID.newULID, Encode.forHtml(name), email, hash(password), born, bio, replyTo)
  }
  final case class Put(id: ULID, name: String, password: String, born: LocalDate, bio: Option[String] = None) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set(
      validate(nameRule(name), name, "name"),
      validate(passwordRule(password), password, "password"),
      validate(bornRule(born), born, "born")
    ).flatten
    def asCmd(replyTo: ActorRef[StatusReply[User.Response]]): Update = Update(id, name, hash(password), born, replyTo)
  }
  final case class Authenticate(email: String, password: String) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set(
      validate(emailRule(email), email, "email"),
      validate(passwordRule(password), password, "password")
    ).flatten
    def asCmd(replyTo: ReplyTokenTo): Login = Login(email, hash(password), replyTo)
  }
  final case class RequestFollow(id: ULID, other: ULID) extends Request {
    def asCmd(replyTo: ReplyAnyTo): Follow = Follow(id, other, replyTo)
  }


  case class Create(id: ULID, name: String, email: String, password: String, born: LocalDate, bio: Option[String], replyTo: ActorRef[StatusReply[User.Response]]) extends Command {
    lazy val joined: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
    def asResponse: Response = Response(id, name, email, joined, born, bio)
    def asEvent: Created = this.into[Created].transform
  }
  case class Update(id: ULID, name: String, password: String, born: LocalDate, replyTo: ActorRef[StatusReply[User.Response]]) extends Command {
    def asEvent: Updated = this.into[Updated].transform
  }
  case class Remove(id: UserId, replyTo: ActorRef[StatusReply[User.Response]]) extends Command {
    def asEvent: Removed = User.Removed(id)
  }

  case class Find(id: ULID, replyTo: ActorRef[StatusReply[User.Response]]) extends Command
  case class All(replyTo: ReplyListTo) extends Command

  case class Login(email: String, password: String, replyTo: ReplyTokenTo) extends Command
  case class Authorize(token: String, replyTo: ReplySessionTo) extends Command
  case class Logout(token: String, replyTo: ReplyAnyTo) extends Command

  case class Follow(id: ULID, other: ULID, replyTo: ReplyAnyTo) extends Command

  case class Created(id: ULID, name: String, email: String, password: String, born: LocalDate, bio: Option[String]) extends Event {
    lazy val asState: User.State = User.State(id, name, email, password, born, bio)
  }
  case class Updated(id: ULID, name: String, password: String, born: LocalDate) extends Event
  case class Removed(id: ULID, email: String = "") extends Event

  case class LoggedIn(id: ULID, expires: LocalDateTime = now.plusHours(2)) extends Event
  case class LoggedOut(id: ULID) extends Event
  case class Followed(id: ULID, other: ULID) extends Event


  final case class State(
        id: ULID,
        name: String,
        email: String,
        password: String,
        born: LocalDate,
        bio: Option[String] = None,
        notes: Vector[String] = Vector.empty,
        comments: Vector[String] = Vector.empty
  ) extends Entity {
    val joined: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
    val token: String = hash(id)
    def response: Response = User.Response(id, name, email, joined, born, bio, notes)
  }

  object State {
    def apply(uc: User.Created): User.State = new User.State(
      id = uc.id, name = uc.name, email = uc.email, password = uc.password, born = uc.born, bio = uc.bio
    )
  }


  final case class Response(
      id: ULID,
      name: String,
      email: String,
      joined: LocalDateTime,
      born: LocalDate,
      bio: Option[String] = None,
      notes: Vector[String] = Vector.empty
  ) extends Respons

  final case class Session(token: String, id: ULID, expires: LocalDateTime = now.plusHours(2)) {
    lazy val asOAuthToken: OAuthToken = OAuthToken(token, id)
    def isValid(t: String): Boolean = token.contentEquals(t) && expires.isAfter(now)
  }


  def apply(state: User.State): Behavior[Command] = Behaviors.setup { ctx =>
    val pid: PersistenceId = PersistenceId("user", state.id.toString(), "-")

    var recovered: Boolean = false
    var session: Option[User.Session] = None

    ctx.system.receptionist.tell(Receptionist.Register(User.key, ctx.self))

    val commandHandler: (User.State, Command) => ReplyEffect[Event, User.State] = (state, cmd) =>
      cmd match {
        case uu: User.Update => Effect.persist(uu.asEvent).thenReply(uu.replyTo)(state => StatusReply.success(state.response))
        case ur: User.Remove => Effect.persist(ur.asEvent).thenReply(ur.replyTo)(_ => StatusReply.success(state.response))
        case uf: User.Find => Effect.reply(uf.replyTo)(StatusReply.success(state.response))
        case ul: User.Login =>
          if (ul.email.contentEquals(state.email) && ul.password.contentEquals(state.password)) {
            Effect.persist(User.LoggedIn(state.id)).thenReply(ul.replyTo) { state =>
              session match {
                case Some(session) => StatusReply.success(session.asOAuthToken)
                case None => StatusReply.error(s"!!!: user[${state.email}] is authenticated but no session found")
              }
            }
          } else {
            Effect.reply(ul.replyTo)(StatusReply.error("invalid credentials"))
          }
        case ua: User.Authorize => Effect.reply(ua.replyTo)(session.filter(_.isValid(ua.token)))
        case ul: User.Logout => session match {
          case Some(us) => Effect.persist(User.LoggedOut(us.id)).thenReply(ul.replyTo)(_ => StatusReply.success("Logged Out"))
          case None => Effect.reply(ul.replyTo)(StatusReply.error("No Session"))
        }
        case nc: Note.Create =>
          if (state.notes.contains(nc.slug)) {
            Effect.reply(nc.replyTo)(StatusReply.error(s"Note with title ${nc.title} for today already exists"))
          } else {
            Effect.persist(nc.asEvent).thenReply(nc.replyTo)(_ => StatusReply.success(nc.asResponse))
          }
      }

    val eventHandler: (User.State, Event) => User.State = (state, evt) => {
      evt match {
        case uu: User.Updated => state.copy(name = uu.name, password = uu.password, born = uu.born)
        case _: User.Removed => ctx.system.receptionist.tell(Receptionist.Deregister(User.key, ctx.self)); state
        case _: User.LoggedIn => session = Some(User.Session(hash(state.id), state.id)); state
        case _: User.LoggedOut => session = None; state
        case nc: Note.Created =>
          ctx.spawn(Note(nc.asState), Note.name(state.id, nc.id, nc.title))
          state.copy(notes = state.notes.appended(nc.slug))
      }
    }

    EventSourcedBehavior.withEnforcedReplies[Command, Event, User.State](pid, state, commandHandler, eventHandler)
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1)
      )
      .withRetention(RetentionCriteria.disabled)
      .withRecovery(Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.none))
      .receiveSignal {
        case (_, RecoveryCompleted) => recovered = true
        case (_, RecoveryFailed(t)) => ctx.log.error(s"recovery of user $pid failed", t)
      }
  }
}
