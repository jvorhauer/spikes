package spikes.model

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, Recovery, ReplyEffect, RetentionCriteria}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted, RecoveryFailed, SnapshotSelectionCriteria}
import org.owasp.encoder.Encode
import scalikejdbc.*
import scalikejdbc.interpolation.SQLSyntax.{count, distinct}
import spikes.validate.Validation.{ErrorInfo, validate}
import wvlet.airframe.ulid.ULID

import java.sql.ResultSet
import java.time.{LocalDate, LocalDateTime, ZoneId}
import scala.concurrent.duration.DurationInt

object User {

  val key: ServiceKey[Command] = ServiceKey("User")
  val tag = "user"

  type UserId = ULID

  type ReplyTo = ActorRef[StatusReply[ResponseT]]
  type ReplyListTo = ActorRef[StatusReply[List[ResponseT]]]
  type ReplyTokenTo = ActorRef[StatusReply[OAuthToken]]
  type ReplySessionTo = ActorRef[Option[User.Session]]
  type ReplyAnyTo = ActorRef[StatusReply[Any]]

  def name(id: UserId, email: String): String = s"user-$id-$email-${id.hashed}"

  final case class Post(name: String, email: String, password: String, born: LocalDate, bio: Option[String] = None) extends Request {
    override def validated: Set[ErrorInfo] = Set(validate("name", name), validate("email", email), validate("password", password), validate("born", born)).flatten
    def asCmd(replyTo: ActorRef[StatusReply[User.Response]]): Create = Create(
      ULID.newULID, Encode.forHtml(name), email, hash(password), born, bio.map(Encode.forHtml), replyTo
    )
  }
  final case class Put(id: ULID, name: String, password: String, born: LocalDate, bio: Option[String] = None) extends Request {
    override def validated: Set[ErrorInfo] = Set(validate("name", name), validate("password", password), validate("born", born)).flatten
    def asCmd(replyTo: ActorRef[StatusReply[User.Response]]): Update = Update(id, name, hash(password), born, bio, replyTo)
  }

  final case class Authenticate(email: String, password: String) extends Request {
    override def validated: Set[ErrorInfo] = Set(validate("email", email), validate("password", password)).flatten
    def asCmd(replyTo: ReplyTokenTo): Login = Login(email, hash(password), replyTo)
  }

  final case class RequestFollow(id: ULID, other: ULID) extends Request {
    def asCmd(replyTo: ReplyAnyTo): Follow = Follow(id, other, replyTo)
  }

  final case class Create(id: ULID, name: String, email: String, password: String, born: LocalDate, bio: Option[String], replyTo: ActorRef[StatusReply[User.Response]]) extends Command {
    def asEvent: Created = Created(id, name, email, password, born, bio)
  }

  final case class Update(id: UserId, name: String, password: String, born: LocalDate, bio: Option[String], replyTo: ActorRef[StatusReply[User.Response]]) extends Command {
    def asEvent: Updated = Updated(id, name, password, born, bio)
  }

  final case class Remove(id: UserId, replyTo: ActorRef[StatusReply[User.Response]]) extends Command {
    def asEvent: Removed = Removed(id)
  }

  final case class Login(email: String, password: String, replyTo: ReplyTokenTo) extends Command
  final case class Authorize(token: String, replyTo: ReplySessionTo) extends Command
  final case class Logout(token: String, replyTo: ReplyAnyTo) extends Command
  final case class Follow(id: UserId, other: UserId, replyTo: ReplyAnyTo) extends Command


  sealed trait UserEvent extends Event

  final case class Created(id: UserId, name: String, email: String, password: String, born: LocalDate, bio: Option[String]) extends UserEvent
  final case class Updated(id: UserId, name: String, password: String, born: LocalDate, bio: Option[String]) extends UserEvent
  final case class Removed(id: UserId, email: String = "") extends UserEvent
  final case class LoggedIn(id: UserId, expires: LocalDateTime = now.plusHours(2)) extends UserEvent
  final case class LoggedOut(id: UserId) extends UserEvent
  final case class Followed(id: UserId, other: UserId) extends UserEvent


  final case class State(
      id: UserId,
      name: String,
      email: String,
      password: String,
      born: LocalDate,
      bio: Option[String] = None,
      session: Option[Session] = None,
      removed: Boolean = false
  ) extends StateT with Entity {
    lazy val joined: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
    lazy val token: String = id.hashed

    def asResponse: Response = User.Response(id, name, email, joined, born, bio)
    def authenticate(email: String, password: String): Boolean = this.email.contentEquals(email) && this.password.contentEquals(password)
  }
  object State extends SQLSyntaxSupport[User.State] {
    override val tableName = "users"

    implicit val userIdTypeBinder: TypeBinder[UserId] = new TypeBinder[UserId] {
      def apply(rs: ResultSet, label: String): UserId = ULID(rs.getString(label))
      def apply(rs: ResultSet, index: Int): UserId = ULID(rs.getString(index))
    }

    def apply(rs: WrappedResultSet): User.State = new User.State(
      ULID(rs.string("id")),
      rs.string("name"),
      rs.string("email"),
      rs.string("password"),
      rs.localDate("born"),
      rs.stringOpt("bio")
    )
    def apply(rs: WrappedResultSet, rn: ResultName[User.State]): User.State = autoConstruct(rs, rn, "session", "removed")

    def apply(uc: User.Created): User.State = new User.State(uc.id, uc.name, uc.email, uc.password, uc.born, uc.bio)
  }

  final case class Response(
      id: UserId,
      name: String,
      email: String,
      joined: LocalDateTime,
      born: LocalDate,
      bio: Option[String] = None,
  ) extends ResponseT

  final case class Session(token: String, id: ULID, expires: LocalDateTime = now.plusHours(2)) extends SpikeSerializable {
    lazy val asOAuthToken: OAuthToken = OAuthToken(token, id)

    def isValid(t: String): Boolean = token.contentEquals(t) && expires.isAfter(now)
  }


  def apply(state: User.State): Behavior[Command] = Behaviors.setup { ctx =>
    val pid: PersistenceId = PersistenceId("user", state.id.toString(), "-")

    ctx.system.receptionist.tell(Receptionist.Register(User.key, ctx.self))

    val commandHandler: (User.State, Command) => ReplyEffect[Event, User.State] = (state, cmd) =>
      cmd match {
        case uc: User.Create => Effect.persist(uc.asEvent).thenReply(uc.replyTo)(state => StatusReply.success(state.asResponse))
        case uu: User.Update => Effect.persist(uu.asEvent).thenReply(uu.replyTo)(state => StatusReply.success(state.asResponse))
        case ur: User.Remove => Effect.persist(ur.asEvent).thenReply(ur.replyTo)(_ => StatusReply.success(state.asResponse))

        case ul: User.Login =>
          if (User.Repository.exists(ul.email, ul.password)) {
            Effect.persist(User.LoggedIn(state.id)).thenReply(ul.replyTo) { state =>
              state.session match {
                case Some(session) => StatusReply.success(session.asOAuthToken)
                case None => StatusReply.error(s"user[${state.email}] is authenticated but no session found")
              }
            }
          } else {
            Effect.reply(ul.replyTo)(StatusReply.error(s"invalid credentials ($ul)"))
          }
        case ua: User.Authorize => Effect.reply(ua.replyTo)(state.session.filter(_.isValid(ua.token)))
        case ul: User.Logout => state.session match {
          case Some(us) => Effect.persist(User.LoggedOut(us.id)).thenReply(ul.replyTo)(_ => StatusReply.success("Logged Out"))
          case None => Effect.reply(ul.replyTo)(StatusReply.error("No Session"))
        }
        case nc: Note.Create => Note.Repository.find(nc.slug) match {
          case Some(_) => Effect.reply(nc.replyTo)(StatusReply.error(s"Note with slug ${nc.slug} already exists"))
          case None => Effect.persist(nc.asEvent).thenReply(nc.replyTo)(_ => StatusReply.success(nc.asResponse))
        }
      }

    val eventHandler: (User.State, Event) => User.State = (state, evt) => {
      evt match {
        case uc: User.Created => User.State(uc)
        case uu: User.Updated => User.Repository.save(uu).filter(_ > 0).flatMap(_ => User.Repository.find(uu.id)).getOrElse(state)
        case _: User.Removed =>
          ctx.system.receptionist.tell(Receptionist.Deregister(User.key, ctx.self))
          state.copy(removed = true)

        case ul: User.LoggedIn => state.copy(session = Some(User.Session(state.id.hashed, state.id)))
        case _: User.LoggedOut => state.copy(session = None)

        case nc: Note.Created =>
          ctx.spawn(Note(Note.State(nc)), Note.name(state.id, nc.id, nc.slug))
          Note.Repository.save(nc)
          state
      }
    }

    EventSourcedBehavior.withEnforcedReplies[Command, Event, User.State](pid, state, commandHandler, eventHandler)
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      .withRetention(RetentionCriteria.disabled)
      .withRecovery(Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.none))
      .receiveSignal {
        case (_, RecoveryCompleted) => ctx.log.debug(s"user ${state.id} recovered")
        case (_, RecoveryFailed(t)) => ctx.log.error(s"recovery of user ${state.id} failed", t)
      }
  }

  object Repository {

    implicit val session: DBSession = AutoSession

    private val u = User.State.syntax("u")
    private val cols = User.State.column

    def save(uc: User.Created): User.State = {
      withSQL(insert.into(User.State).namedValues(
        cols.id -> uc.id.toString(),
        cols.name -> uc.name,
        cols.email -> uc.email,
        cols.password -> uc.password,
        cols.born -> uc.born,
        cols.bio -> uc.bio)
      ).update.apply()
      User.State(uc)
    }
    def save(uu: User.Updated): Option[Int] = find(uu.id)
      .map(state => withSQL(update(User.State).set(
        cols.name -> uu.name,
        cols.bio -> uu.bio,
        cols.born -> uu.born,
        cols.password -> uu.password
      ).where.eq(cols.id, state.id.toString())).update.apply())

    def find(id: ULID): Option[State] = withSQL(select.from(State as u).where.eq(cols.id, id.toString)).map(rs => State(rs, u.resultName)).single.apply()
    def find(e: String): Option[State] = withSQL(select.from(State as u).where.eq(cols.email, e)).map(rs => State(rs)).single.apply()
    def exists(e: String, p: String): Boolean =
      withSQL(select.from(State as u).where.eq(cols.email, e).and.eq(cols.password, p)).map(rs => State(rs)).single.apply().isDefined
    def list(limit: Int = 10, offset: Int = 0): List[User.State] = withSQL(select.from(State as u).limit(limit).offset(offset)).map(State(_)).list.apply()
    def size(): Int = withSQL(select(count(distinct(cols.id))).from(State as u)).map(_.int(1)).single.apply().getOrElse(0)

    def remove(id: ULID): Unit = withSQL(delete.from(State as u).where.eq(cols.id, id.toString)).update.apply()
    def nuke(): Unit = withSQL(delete.from(User.State)).update.apply()
  }
}
