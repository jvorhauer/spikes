package spikes.model

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import akka.persistence.typed.{PersistenceId, RecoveryFailed}
import com.typesafe.config.{Config, ConfigFactory}
import io.hypersistence.tsid.TSID
import io.scalaland.chimney.dsl.TransformerOps
import org.owasp.encoder.Encode
import scalikejdbc.*
import scalikejdbc.interpolation.SQLSyntax.{count, distinct}
import spikes.behavior.Manager.lookup
import spikes.model.User.UserId
import spikes.validate.Validation.{ErrorInfo, validate}

import java.time.temporal.TemporalAmount
import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

final case class User(id: UserId, name: String, email: String, password: String, born: LocalDate, bio: Option[String] = None, notes: Int = 0) extends SpikeSerializable {
  def toResponse: User.Response = this.into[User.Response].withFieldComputed(_.joined, _ => id.created).transform
  def remove(): Unit = User.remove(id)
}

object User extends SQLSyntaxSupport[User] {

  override val tableName = "users"
  implicit val session: DBSession = AutoSession

  private val u = User.syntax("u")
  private val cols = User.column

  private val cfg: Config = ConfigFactory.defaultApplication()
  private val expire: TemporalAmount = cfg.getTemporal("spikes.token.expires")

  val key: ServiceKey[Command] = ServiceKey("User")
  val tag = "user"

  type UserId = SPID
  type ReplyTo = ActorRef[StatusReply[User.Response]]
  type ReplyListTo = ActorRef[StatusReply[List[User.Response]]]
  type ReplyTokenTo = ActorRef[StatusReply[OAuthToken]]
  type ReplySessionTo = ActorRef[Option[Session]]
  type ReplyAnyTo = ActorRef[StatusReply[Any]]

  def name(id: UserId, email: String): String = s"user-$id-$email"

  def apply(rs: WrappedResultSet): User = new User(
    TSID.from(rs.long("id")),
    rs.string("name"),
    rs.string("email"),
    rs.string("password"),
    rs.localDate("born"),
    rs.stringOpt("bio")
  )
  def apply(uc: User.Created): User = new User(uc.id, uc.name, uc.email, uc.password, uc.born, uc.bio)

  def save(uc: User.Created): User = {
    withSQL(insert.into(User).namedValues(
      cols.id -> uc.id,
      cols.name -> uc.name,
      cols.email -> uc.email,
      cols.password -> uc.password,
      cols.born -> uc.born,
      cols.bio -> uc.bio)).update.apply()
    User(uc)
  }
  def save(uu: User.Updated): Option[Int] = find(uu.id).map(us =>
    withSQL(update(User).set(
      cols.name -> uu.name.getOrElse(us.name),
      cols.password -> uu.password.getOrElse(us.password),
      cols.born -> uu.born.getOrElse(us.born),
      cols.bio -> uu.bio
    ).where.eq(cols.id, uu.id)).update.apply()
  )

  def find(id: UserId): Option[User] = withSQL(select.from(User as u).where.eq(cols.id, id)).map(User(_)).single.apply()
  def exists(id: UserId): Boolean = find(id).isDefined
  def find(email: String): Option[User] = withSQL(select.from(User as u).where.eq(cols.email, email)).map(User(_)).single.apply()
  def exists(e: String, p: String): Boolean = withSQL(select.from(User as u).where.eq(cols.email, e).and.eq(cols.password, p)).map(User(_)).single.apply().isDefined
  def list(limit: Int = 10, offset: Int = 0): List[User] = withSQL(select.from(User as u).limit(limit).offset(offset)).map(User(_)).list.apply()
  def size: Int = withSQL(select(count(distinct(cols.id))).from(User as u)).map(_.int(1)).single.apply().getOrElse(0)

  def remove(id: UserId): Unit = withSQL(delete.from(User as u).where.eq(cols.id, id)).update.apply()
  def removeAll(): Unit = withSQL(delete.from(User)).update.apply()


  final case class Post(name: String, email: String, password: String, born: LocalDate, bio: Option[String] = None) extends Request {
    override def validated: Set[ErrorInfo] = Set(validate("name", name), validate("email", email), validate("password", password), validate("born", born)).flatten
    def asCmd(replyTo: ReplyTo): Create = Create(next, Encode.forHtml(name), email, hash(password), born, bio.map(Encode.forHtml), replyTo)
  }
  final case class Put(id: UserId, name: Option[String], password: Option[String], born: Option[LocalDate], bio: Option[String] = None) extends Request {
    override def validated: Set[ErrorInfo] = Set(name.flatMap(validate("name", _)), password.flatMap(validate("password", _)), born.flatMap(validate("born", _))).flatten
    def asCmd(replyTo: ReplyTo): Update = Update(id, name.map(Encode.forHtml), password.map(hash), born, bio.map(Encode.forHtml), replyTo)
  }
  final case class Authenticate(email: String, password: String) extends Request {
    override def validated: Set[ErrorInfo] = Set(validate("email", email), validate("password", password)).flatten
    def asCmd(replyTo: ReplyTokenTo): Login = Login(email, hash(password), replyTo)
  }


  final case class Create(id: UserId, name: String, email: String, password: String, born: LocalDate, bio: Option[String], replyTo: ReplyTo) extends Command {
    def asEvent: Created = this.into[User.Created].transform
  }
  final case class Update(id: UserId, name: Option[String], password: Option[String], born: Option[LocalDate], bio: Option[String], replyTo: ReplyTo) extends Command {
    def asEvent: Updated = this.into[User.Updated].transform
  }
  final case class Remove(id: UserId, replyTo: ReplyTo) extends Command {
    def asEvent: Removed = this.into[User.Removed].transform
  }
  final case class Login(email: String, password: String, replyTo: ReplyTokenTo) extends Command
  final case class Logout(token: String, replyTo: ReplyAnyTo) extends Command


  final case class Created(id: UserId, name: String, email: String, password: String, born: LocalDate, bio: Option[String]) extends Event {
    def save: User = User.save(this)
  }
  final case class Updated(id: UserId, name: Option[String], password: Option[String], born: Option[LocalDate], bio: Option[String]) extends Event {
    def save: Boolean = User.save(this).isDefined
  }
  final case class Removed(id: UserId) extends Event
  final case class LoggedIn(id: UserId, expires: LocalDateTime) extends Event
  final case class LoggedOut(id: UserId) extends Event


  final case class Response(id: UserId, name: String, email: String, joined: LocalDateTime, born: LocalDate, bio: Option[String], notes: Int = 0) extends ResponseT
  object Response {
    def apply(state: User): Response = state.into[Response]
      .enableDefaultValues
      .withFieldComputed(_.joined, _.id.created)
      .transform
    def apply(created: User.Created): Response = created.into[Response]
      .enableDefaultValues
      .withFieldComputed(_.joined, _.id.created)
      .transform
  }


  def apply(state: User): Behavior[Command] = Behaviors.setup { ctx =>
    val pid: PersistenceId = PersistenceId("user", state.id.toString)
    implicit val ec: ExecutionContext = ctx.executionContext

    ctx.system.receptionist.tell(Receptionist.Register(User.key, ctx.self))

    val commandHandler: (User, Command) => ReplyEffect[Event, User] = (state, cmd) => cmd match {
        case uc: User.Create => Effect.persist(uc.asEvent).thenReply(uc.replyTo)(upstate => StatusReply.success(Response(upstate)))
        case uu: User.Update => Effect.persist(uu.asEvent).thenReply(uu.replyTo)(upstate => StatusReply.success(Response(upstate)))
        case ur: User.Remove => Effect.persist(ur.asEvent).thenReply(ur.replyTo)(_ => StatusReply.success(Response(state)))

        case ul: User.Login =>
          if (User.exists(ul.email, ul.password)) {
            Effect.persist(User.LoggedIn(state.id, now.plus(expire))).thenReply(ul.replyTo) { _ =>
              Session.find(state.id) match {
                case Some(session) => StatusReply.success(session.asOAuthToken)
                case None => StatusReply.error(s"${state.email} is authenticated but no session found")
              }
            }
          } else {
            Effect.reply(ul.replyTo)(StatusReply.error("Invalid credentials"))
          }
        case ul: User.Logout => Session.find(state.id) match {
          case Some(us) => Effect.persist(User.LoggedOut(us.id)).thenReply(ul.replyTo)(_ => StatusReply.success("Logged Out"))
          case None => Effect.reply(ul.replyTo)(StatusReply.error("No Session"))
        }

        case nc: Note.Create => Note.find(nc.slug) match {
          case Some(_) => Effect.reply(nc.replyTo)(StatusReply.error(s"Note with slug ${nc.slug} already exists"))
          case None    => Effect.persist(nc.asEvent).thenReply(nc.replyTo)(_ => StatusReply.success(nc.asResponse))
        }
        case nr: Note.Remove => Note.find(nr.id, nr.owner) match {
          case Some(_) => Effect.persist(nr.asEvent).thenRun((_: User) => lookup(nr.id, Note.key, ctx).map(_.foreach(_.tell(nr)))).thenNoReply()
          case None    => Effect.reply(nr.replyTo)(StatusReply.error(s"Note ${nr.id} could not be deleted"))
        }
      }

    val eventHandler: (User, Event) => User = (state, evt) => evt match {
        case uc: User.Created => User(uc)
        case uu: User.Updated => User.save(uu).filter(_ > 0).flatMap(_ => User.find(uu.id)).getOrElse(state)
        case _: User.Removed =>
          ctx.system.receptionist.tell(Receptionist.Deregister(User.key, ctx.self))
          state
        case ul: User.LoggedIn  =>
          User.find(ul.id).foreach(us => Session.save(us, ul.expires))
          state
        case uo: User.LoggedOut =>
          Session.remove(uo.id)
          state
        case nc: Note.Created =>
          ctx.spawn(Note(Note.save(nc)), Note.name(state.id, nc.id, nc.slug))
          state.copy(notes = state.notes + 1)
        case nr: Note.Removed =>
          lookup(nr.id, Note.key, ctx).map(_.foreach(ctx.stop(_)))
          Note.remove(nr.id)
          state.copy(notes = state.notes -1)
      }

    EventSourcedBehavior.withEnforcedReplies[Command, Event, User](pid, state, commandHandler, eventHandler)
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))
      .withTagger(_ => Set(User.tag))
      .receiveSignal {
        case (_, rf: RecoveryFailed) => ctx.log.error(s"recovery failed for user ${pid.id}", rf.failure)
      }
  }

  val ddl: Seq[SQLExecution] = Seq(
    sql"""create table if not exists users (
      id bigint primary key,
      name varchar(1024) not null,
      email varchar(1024) unique not null,
      password varchar(64) not null,
      born date not null,
      bio varchar(4096)
    )""".execute,
    sql"create unique index if not exists users_email_idx on users (email)".execute
  )
}
