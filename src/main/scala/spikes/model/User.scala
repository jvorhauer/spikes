package spikes.model

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, Recovery, ReplyEffect, RetentionCriteria}
import akka.persistence.typed.{PersistenceId, RecoveryFailed, SnapshotSelectionCriteria}
import io.scalaland.chimney.dsl.TransformerOps
import org.owasp.encoder.Encode
import scalikejdbc.*
import scalikejdbc.interpolation.SQLSyntax.{count, distinct}
import spikes.behavior.Manager.lookup
import spikes.validate.Validation.{ErrorInfo, validate}
import wvlet.airframe.ulid.ULID

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.ExecutionContext
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

  def name(id: UserId, email: String): String = s"user-$id-$email"

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

  final case class Create(id: ULID, name: String, email: String, password: String, born: LocalDate, bio: Option[String], replyTo: ActorRef[StatusReply[User.Response]]) extends Command {
    def asEvent: Created = this.into[User.Created].transform
  }

  final case class Update(id: UserId, name: String, password: String, born: LocalDate, bio: Option[String], replyTo: ActorRef[StatusReply[User.Response]]) extends Command {
    def asEvent: Updated = this.into[User.Updated].transform
  }

  final case class Remove(id: UserId, replyTo: ActorRef[StatusReply[User.Response]]) extends Command {
    def asEvent: Removed = this.into[User.Removed].transform
  }

  final case class Login(email: String, password: String, replyTo: ReplyTokenTo) extends Command
  final case class Logout(token: String, replyTo: ReplyAnyTo) extends Command

  sealed trait UserEvent extends Event

  final case class Created(id: UserId, name: String, email: String, password: String, born: LocalDate, bio: Option[String]) extends UserEvent
  final case class Updated(id: UserId, name: String, password: String, born: LocalDate, bio: Option[String]) extends UserEvent
  final case class Removed(id: UserId) extends UserEvent
  final case class LoggedIn(id: UserId) extends UserEvent
  final case class LoggedOut(id: UserId) extends UserEvent


  final case class State(
      id: UserId,
      name: String,
      email: String,
      password: String,
      born: LocalDate,
      bio: Option[String] = None,
  ) extends StateT with Entity {
    lazy val token: String = id.hashed
  }
  object State extends SQLSyntaxSupport[User.State] {
    override val tableName = "users"

    def apply(rs: WrappedResultSet): User.State = new User.State(
      ULID(rs.string("id")),
      rs.string("name"),
      rs.string("email"),
      rs.string("password"),
      rs.localDate("born"),
      rs.stringOpt("bio")
    )
    def apply(uc: User.Created): User.State = new User.State(uc.id, uc.name, uc.email, uc.password, uc.born, uc.bio)
  }

  final case class Response(
      id: UserId, name: String, email: String, joined: LocalDateTime, born: LocalDate, bio: Option[String]
  ) extends ResponseT
  object Response {
    def apply(state: User.State): Response = state.into[Response]
      .withFieldComputed(_.joined, _.id.created)
      .transform
    def apply(created: User.Created): Response = created.into[Response]
      .enableDefaultValues
      .withFieldComputed(_.joined, _.id.created)
      .transform
  }

  final case class Session(id: ULID, token: String, expires: LocalDateTime = now.plusHours(2)) extends Entity {
    lazy val asOAuthToken: OAuthToken = OAuthToken(token, id)
    def isValid(t: String): Boolean = token.contentEquals(t) && expires.isAfter(now)
  }
  object Session extends SQLSyntaxSupport[User.Session] {
    override val tableName = "sessions"
    private  val s = User.Session.syntax("s")
    private  val cols = User.Session.column
    implicit val session: DBSession = AutoSession

    def save(us: User.State): User.Session = {
      find(us.id) match {
        case None =>
          val ses = User.Session (us.id, hash (next) )
          withSQL(insert.into (User.Session).namedValues (cols.id -> us.id, cols.token -> ses.token, cols.expires -> ses.expires) ).update.apply ()
          ses
        case Some(es) =>
          withSQL(update(User.Session).set(cols.expires -> now.plusHours(2)).where.eq(cols.id, es.id)).update.apply()
          es
      }
    }
    def find(token: String): Option[User.Session] = withSQL(select.from(Session as s).where.eq(cols.token, token)).map(Session(_)).single.apply()
    def find(id: UserId): Option[User.Session] = withSQL(select.from(Session as s).where.eq(cols.id, id)).map(Session(_)).single.apply()
    def remove(id: UserId): Unit = withSQL(delete.from(Session as s).where.eq(cols.id, id)).update.apply()
    def size(): Int = withSQL(select(count(distinct(cols.id))).from(Session as s)).map(_.int(1)).single.apply().getOrElse(0)
    def expired(): Int = withSQL(select(count(distinct(cols.id))).from(Session as s).where.gt(cols.expires, now)).map(_.int(1)).single.apply().getOrElse(0)
    def reap(): Unit = withSQL(delete.from(Session as s).where.gt(cols.expires, now)).update.apply()

    def apply(rs: WrappedResultSet): User.Session = new User.Session(ULID(rs.string("id")), rs.string("token"), rs.localDateTime("expires"))
  }


  def apply(state: User.State): Behavior[Command] = Behaviors.setup { ctx =>
    val pid: PersistenceId = PersistenceId("user", state.id.toString(), "-")

    ctx.system.receptionist.tell(Receptionist.Register(User.key, ctx.self))

    implicit val ec: ExecutionContext = ctx.executionContext

    val commandHandler: (User.State, Command) => ReplyEffect[Event, User.State] = (state, cmd) => cmd match {
        case uc: User.Create => Effect.persist(uc.asEvent).thenReply(uc.replyTo)(upstate => StatusReply.success(Response(upstate)))
        case uu: User.Update => Effect.persist(uu.asEvent).thenReply(uu.replyTo)(upstate => StatusReply.success(Response(upstate)))
        case ur: User.Remove => Effect.persist(ur.asEvent).thenReply(ur.replyTo)(_ => StatusReply.success(Response(state)))

        case ul: User.Login =>
          if (User.Repository.exists(ul.email, ul.password)) {
            Effect.persist(User.LoggedIn(state.id)).thenReply(ul.replyTo) { _ =>
              User.Session.find(state.id) match {
                case Some(session) => StatusReply.success(session.asOAuthToken)
                case None => StatusReply.error(s"user[${state.email}] is authenticated but no session found")
              }
            }
          } else {
            Effect.reply(ul.replyTo)(StatusReply.error("invalid credentials"))
          }
        case ul: User.Logout => User.Session.find(state.id) match {
          case Some(us) => Effect.persist(User.LoggedOut(us.id)).thenReply(ul.replyTo)(_ => StatusReply.success("Logged Out"))
          case None => Effect.reply(ul.replyTo)(StatusReply.error("No Session"))
        }

        case nc: Note.Create => Note.Repository.find(nc.slug) match {
          case Some(_) => Effect.reply(nc.replyTo)(StatusReply.error(s"Note with slug ${nc.slug} already exists"))
          case None    => Effect.persist(nc.asEvent).thenReply(nc.replyTo)(_ => StatusReply.success(nc.asResponse))
        }
        case nr: Note.Remove => Note.Repository.find(nr.id, nr.owner) match {
          case Some(_) => Effect.persist(nr.asEvent).thenRun((_: State) => lookup(nr.id, Note.key, ctx).map(_.foreach(_.tell(nr)))).thenNoReply()
          case None    => Effect.reply(nr.replyTo)(StatusReply.error(s"Note ${nr.id} could not be deleted"))
        }
      }

    val eventHandler: (User.State, Event) => User.State = (state, evt) => evt match {
        case uc: User.Created => User.State(uc)
        case uu: User.Updated => User.Repository.save(uu).filter(_ > 0).flatMap(_ => User.Repository.find(uu.id)).getOrElse(state)
        case _: User.Removed =>
          ctx.system.receptionist.tell(Receptionist.Deregister(User.key, ctx.self))
          state
        case ul: User.LoggedIn  =>
          User.Repository.find(ul.id).foreach(User.Session.save)
          state
        case uo: User.LoggedOut =>
          User.Session.remove(uo.id)
          state
        case nc: Note.Created =>
          ctx.spawn(Note(Note.Repository.save(nc)), Note.name(state.id, nc.id, nc.slug))
          state
        case nr: Note.Removed =>
          lookup(nr.id, Note.key, ctx).map(_.foreach(ctx.stop(_)))
          Note.Repository.remove(nr.id)
          state
      }

    EventSourcedBehavior.withEnforcedReplies[Command, Event, User.State](pid, state, commandHandler, eventHandler)
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      .withRetention(RetentionCriteria.disabled)
      .withRecovery(Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.none))
      .withTagger(_ => Set(User.tag))
      .receiveSignal {
        case (_, RecoveryFailed(t)) => ctx.log.error(s"recovery of ${pid.id} failed", t)
      }
  }

  object Repository {

    implicit val session: DBSession = AutoSession

    private val u = User.State.syntax("u")
    private val cols = User.State.column

    def save(uc: User.Created): User.State = {
      withSQL(insert.into(User.State).namedValues(
        cols.id -> uc.id,
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
      ).where.eq(cols.id, state.id)).update.apply())

    def find(id: UserId): Option[State] = withSQL(select.from(State as u).where.eq(cols.id, id)).map(rs => State(rs)).single.apply()
    def find(e: String): Option[State] = withSQL(select.from(State as u).where.eq(cols.email, e)).map(rs => State(rs)).single.apply()
    def exists(e: String, p: String): Boolean =
      withSQL(select.from(State as u).where.eq(cols.email, e).and.eq(cols.password, p)).map(State(_)).single.apply().isDefined

    def list(limit: Int = 10, offset: Int = 0): List[User.State] = withSQL(select.from(State as u).limit(limit).offset(offset)).map(State(_)).list.apply()
    def size(): Int = withSQL(select(count(distinct(cols.id))).from(State as u)).map(_.int(1)).single.apply().getOrElse(0)

    def remove(id: UserId): Unit = withSQL(delete.from(State as u).where.eq(cols.id, id)).update.apply()
    def removeAll(): Unit = withSQL(delete.from(User.State)).update.apply()
  }
}
