package spikes.model

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.pattern.StatusReply
import akka.persistence.typed.PublishedEvent
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.scalaland.chimney.dsl.TransformerOps
import spikes._
import spikes.behavior.{AllUsers, Query}
import spikes.validate.ModelValidation.validated
import spikes.validate.{FieldRule, ModelValidation}

import java.time.LocalDateTime.now
import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import scala.collection.immutable.HashSet
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object Regexes {
  val name = "^[a-zA-Z '-]+$"
  val email = "^([\\w-]+(?:\\.[\\w-]+)*)@\\w[\\w.-]+\\.[a-zA-Z]+$"
  val poco = "^[1-9][0-9]{3} ?[a-zA-Z]{2}$"
  val passw = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#&()–[{}]:;',?/*~$^+=<>]).{8,42}$"
}

trait UserRequest extends Request {
  def name: String
  def email: String
  def password: String
  def born: LocalDate
  def isValid: Boolean
  def isInvalid: Boolean = !isValid
}

object UserRequest {
  val rules = Set(
    FieldRule("name", (name: String) => name.matches(Regexes.name), "invalid name"),
    FieldRule("email", (email: String) => email.matches(Regexes.email), "invalid email address"),
    FieldRule("password", (password: String) => password.matches(Regexes.passw), "invalid password"),
    FieldRule("born", (born: LocalDate) =>
      born.isBefore(LocalDate.now().minusYears(8)) && born.isAfter(LocalDate.now().minusYears(121)),
      "too old or young"
    )
  )
}

case class RequestCreateUser(name: String, email: String, password: String, born: LocalDate) extends UserRequest {
  lazy val isValid: Boolean = ModelValidation.validate(this, UserRequest.rules).isEmpty
  def asCmd(replyTo: ActorRef[StatusReply[UserResponse]]): CreateUser = this.into[CreateUser]
    .withFieldComputed(_.id, _ => UUID.randomUUID())
    .withFieldComputed(_.password, req => Hasher.hash(req.password))
    .withFieldComputed(_.replyTo, _ => replyTo)
    .transform
}

case class UserInputError(message: String)

case class RequestUpdateUser(id: UUID, name: String, email: String, password: String, born: LocalDate) extends UserRequest {
  lazy val isValid: Boolean = ModelValidation.validate(this, RequestUpdateUser.rules).isEmpty
  def asCmd(replyTo: ActorRef[StatusReply[UserResponse]]): UpdateUser = this.into[UpdateUser]
    .withFieldComputed(_.replyTo, _ => replyTo)
    .transform
}
object RequestUpdateUser {
  val rules = UserRequest.rules ++ Set(FieldRule("id", (id: UUID) => id != null, "no id specified"))
}

case class RequestDeleteUser(email: String) extends Request {
  def asCmd(replyTo: ActorRef[StatusReply[UserResponse]]): DeleteUser = this.into[DeleteUser]
    .withFieldComputed(_.replyTo, _ => replyTo)
    .transform
}
object RequestDeleteUser {
  val rules = Set(FieldRule("email", (email: String) => email.matches(Regexes.email), "invalid email address"))
}

case class RequestLogin(email: String, password: String) extends Request {
  def asCmd(replyTo: ActorRef[StatusReply[String]]): Login = Login(email, Hasher.hash(password), replyTo)
}
object RequestLogin {
  val rules = Set(
    FieldRule("email", (email: String) => email.matches(Regexes.email), "invalid email address"),
    FieldRule("password", (password: String) => password.matches(Regexes.passw), "invalid password")
  )
}


case class CreateUser(
  id: UUID,
  name: String,
  email: String,
  born: LocalDate,
  password: String,
  replyTo: ActorRef[StatusReply[UserResponse]]
) extends Command {
  lazy val asEvent: UserCreated = this.into[UserCreated].withFieldComputed(_.joined, _ => now()).transform
}

case class UpdateUser(
  id: UUID,
  name: String,
  email: String,
  born: LocalDate,
  password: String,
  replyTo: ActorRef[StatusReply[UserResponse]]
) extends Command {
  lazy val asEvent: UserUpdated = this.into[UserUpdated].transform
}

case class DeleteUser(email: String, replyTo: ActorRef[StatusReply[UserResponse]]) extends Command {
  lazy val asEvent: UserDeleted = this.into[UserDeleted].transform
}

case class Login(email: String, password: String, replyTo: ActorRef[StatusReply[String]]) extends Command {
  lazy val asEvent: LoggedIn = LoggedIn(email)
}

case class FindUserById(id: UUID, replyTo: ActorRef[StatusReply[UserResponse]]) extends Command

case class FindAllUser(replyTo: ActorRef[StatusReply[List[UserResponse]]]) extends Command

case class FindUserByEmail(email: String, replyTo: ActorRef[StatusReply[UserResponse]]) extends Command

case class Reap(replyTo: ActorRef[Command]) extends Command


case class UserCreated(
  id: UUID, name: String, email: String, password: String, joined: LocalDateTime, born: LocalDate
) extends Event {
  lazy val asEntity = this.into[User].withFieldComputed(_.entries, _ => Map[UUID, Entry]()).transform
}

case class UserUpdated(id: UUID, name: String, email: String, password: String, born: LocalDate) extends Event

case class UserDeleted(email: String) extends Event

case class LoggedIn(email: String, expires: LocalDateTime = LocalDateTime.now().plusHours(2)) extends Event

case class Reaped(eligible: Int, performed: LocalDateTime = LocalDateTime.now()) extends Event


case class User(
  id: UUID,
  name: String,
  email: String,
  password: String,
  joined: LocalDateTime,
  born: LocalDate,
  entries: Map[UUID, Entry] = Map.empty
) extends Entity {
  lazy val asResponse: UserResponse = this.into[UserResponse].transform
  def asSession(expires: LocalDateTime): UserSession = UserSession(Hasher.hash(UUID.randomUUID().toString), id, expires, this)
}

case class Users(ids: Map[UUID, User] = Map.empty, emails: Map[String, User] = Map.empty) extends CborSerializable {
  def add(u: User): Users = Users(ids + (u.id -> u), emails + (u.email -> u))
  def find(id: UUID): Option[User] = ids.get(id)
  def find(email: String): Option[User] = emails.get(email)
  def exists(id: UUID): Boolean = find(id).isDefined
  def exists(email: String): Boolean = find(email).isDefined
  def remove(id: UUID): Users = find(id).map(u => Users(ids - u.id, emails - u.email)).getOrElse(this)
  def remove(email: String): Users = find(email).map(u => Users(ids - u.id, emails - u.email)).getOrElse(this)
  def concat(other: Users): Users = Users(ids ++ other.ids, emails ++ other.emails)
  def ++(other: Users): Users = concat(other)

  lazy val size: Int = ids.size
  lazy val valid: Boolean = ids.size == emails.size
}

case class UserResponse(id: UUID, name: String, email: String, joined: LocalDateTime, born: LocalDate) extends Response

case class OAuthToken(access_token: String, token_type: String = "bearer", expires_in: Int = 7200) extends CborSerializable

case class UserSession(token: String, id: UUID, expires: LocalDateTime = LocalDateTime.now().plusHours(2), user: User) {
  lazy val asOAuthToken = OAuthToken(token).asJson.toString()
}

case class State(users: Users = Users(), sessions: Set[UserSession] = HashSet.empty) extends CborSerializable {
  def now: LocalDateTime = LocalDateTime.now()
  def get(email: String): Option[User] = users.find(email)
  def get(id: UUID): Option[User] = users.find(id)
  def put(u: User): State = State(users.add(u), sessions)
  def rem(id: UUID): State = State(users.remove(id), sessions)
  def rem(email: String): State = State(users.remove(email), sessions)
  def authenticate(u: User, expires: LocalDateTime): State = State(users, sessions + u.asSession(expires))
  def authorize(token: String): Option[UserSession] = sessions.find(us => us.token == token && us.expires.isAfter(now))
  def authorize(id: UUID): Option[UserSession] = sessions.find(us => us.id == id && us.expires.isAfter(now))
}


case class UserRoutes(handlers: ActorRef[Command], reader: ActorRef[Query])(implicit system: ActorSystem[_]) {

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  implicit val timeout: Timeout = 3.seconds

  private def respond(sc: StatusCode, body: String) =
    complete(HttpResponse(sc, entity = HttpEntity(ContentTypes.`application/json`, body)))

  private val badRequest = complete(HttpResponse(StatusCodes.BadRequest))

  private def replier(fut: Future[StatusReply[UserResponse]], sc: StatusCode) =
    onSuccess(fut) {
      case sur: StatusReply[UserResponse] if sur.isSuccess => respond(sc, sur.getValue.asJson.toString())
      case sur: StatusReply[UserResponse] => respond(StatusCodes.Conflict, UserInputError(sur.getError.getMessage).asJson.toString())
      case _ => badRequest
    }

  val route = handleRejections(ModelValidation.rejectionHandler) {
    pathPrefix("users") {
      concat(
        post {
          entity(as[RequestCreateUser]) { rcu =>
            validated(rcu, UserRequest.rules) { valid =>
              replier(handlers.ask(valid.asCmd), StatusCodes.Created)
            }
          }
        },
        put {
          entity(as[RequestUpdateUser]) { ruu =>
            validated(ruu, RequestUpdateUser.rules) { valid =>
              replier(handlers.ask(valid.asCmd), StatusCodes.OK)
            }
          }
        },
        delete {
          entity(as[RequestDeleteUser]) { rdu =>
            validated(rdu, RequestDeleteUser.rules) { valid =>
              replier(handlers.ask(valid.asCmd), StatusCodes.Accepted)
            }
          }
        },
        (get & path(JavaUUID)) { id =>
          replier(handlers.ask(FindUserById(id, _)), StatusCodes.OK)
        },
        get {
          onSuccess(reader.ask(AllUsers)) {
            case lur: List[UserResponse] => respond(StatusCodes.OK, lur.asJson.toString())
            case _ => badRequest
          }
        },
        path("login") {
          post {
            entity(as[RequestLogin]) { rl =>
              validated(rl, RequestLogin.rules) { valid =>
                onSuccess(handlers.ask(valid.asCmd)) {
                  case ss: StatusReply[String] if ss.isSuccess => respond(StatusCodes.OK, ss.getValue)
                  case ss: StatusReply[_] => respond(StatusCodes.BadRequest, UserInputError(ss.getError.getMessage).asJson.toString())
                  case _ => badRequest
                }
              }
            }
          }
        }
      )
    }
  }
}
