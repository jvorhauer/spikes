package spikes.model

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials
import akka.pattern.StatusReply
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.scalaland.chimney.dsl.TransformerOps
import spikes.behavior.{AllUsers, Query}
import spikes.model.Command.FindUserById
import spikes.validate.ModelValidation
import spikes.validate.ModelValidation.validated

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import scala.collection.immutable.HashSet
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt


final case class User(id: UUID, name: String, email: String, password: String, joined: LocalDateTime, born: LocalDate) extends Entity {
  lazy val asResponse: Response.User = this.into[Response.User].transform
  def asSession(expires: LocalDateTime): UserSession = UserSession(hash(UUID.randomUUID().toString), id, expires, this)
}

final case class Users(ids: Map[UUID, User] = Map.empty, emails: Map[String, User] = Map.empty) extends CborSerializable {
  def save(u: User): Users = Users(ids + (u.id -> u), emails + (u.email -> u))
  def find(id: UUID): Option[User] = ids.get(id)
  def find(email: String): Option[User] = emails.get(email)
  def remove(id: UUID): Users = find(id).map(u => Users(ids - u.id, emails - u.email)).getOrElse(this)
  def remove(email: String): Users = find(email).map(u => Users(ids - u.id, emails - u.email)).getOrElse(this)
  def concat(other: Users): Users = Users(ids ++ other.ids, emails ++ other.emails)
  def ++(others: Users): Users = concat(others)

  lazy val size: Int = ids.size
  lazy val valid: Boolean = ids.size == emails.size
}

final case class OAuthToken(access_token: String, token_type: String = "bearer", expires_in: Int = 7200) extends CborSerializable

final case class UserSession(token: String, id: UUID, expires: LocalDateTime = LocalDateTime.now().plusHours(2), user: User) {
  lazy val asOAuthToken = OAuthToken(token)
}

final case class State(
  users: Users = Users(),
  sessions: Set[UserSession] = HashSet.empty
) extends CborSerializable {
  def find(email: String): Option[User] = users.find(email)
  def find(id: UUID): Option[User] = users.find(id)
  def save(u: User): State = State(users.save(u), sessions)
  def delete(email: String): State = State(users.remove(email), sessions)

  def authenticate(u: User, expires: LocalDateTime): State = State(users, sessions + u.asSession(expires))
  def authorize(token: String): Option[UserSession] = sessions.find(us => us.token == token && us.expires.isAfter(now()))
  def authorize(id: UUID): Option[UserSession] = sessions.find(us => us.id == id && us.expires.isAfter(now()))
}


final case class RequestError(message: String)

final case class UserRoutes(handlers: ActorRef[Command], reader: ActorRef[Query])(implicit system: ActorSystem[_]) {

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  implicit val ec = system.executionContext
  implicit val timeout: Timeout = 3.seconds

  private def respond(sc: StatusCode, body: String) =
    complete(HttpResponse(sc, entity = HttpEntity(ContentTypes.`application/json`, body)))

  private val badRequest = complete(HttpResponse(StatusCodes.BadRequest))

  private def replier(fut: Future[StatusReply[Response.User]], sc: StatusCode) =
    onSuccess(fut) {
      case sur: StatusReply[Response.User] if sur.isSuccess => respond(sc, sur.getValue.asJson.toString())
      case sur: StatusReply[Response.User] => respond(StatusCodes.Conflict, RequestError(sur.getError.getMessage).asJson.toString())
      case _ => badRequest
    }

  private val authenticator: AsyncAuthenticator[UserSession] = {
    case Credentials.Provided(token) =>
      handlers.ask(rt => Command.Authenticate(token, rt))
    case _ => Future.successful(None)
  }

  val route = handleRejections(ModelValidation.rejectionHandler) {
    pathPrefix("users") {
      concat(
        (post & pathEndOrSingleSlash) {
          entity(as[Request.CreateUser]) { rcu =>
            validated(rcu, rcu.rules) { valid =>
              replier(handlers.ask(valid.asCmd), StatusCodes.Created)
            }
          }
        },
        put {
          entity(as[Request.UpdateUser]) { ruu =>
            validated(ruu, ruu.rules) { valid =>
              replier(handlers.ask(valid.asCmd), StatusCodes.OK)
            }
          }
        },
        delete {
          authenticateOAuth2Async(realm = "spikes", authenticator) { us =>
            println(s"authenticated ${us.user.id}")
            entity(as[Request.DeleteUser]) { rdu =>
              validated(rdu, rdu.rules) { valid =>
                replier(handlers.ask(valid.asCmd), StatusCodes.Accepted)
              }
            }
          }
        },
        (get & path(JavaUUID)) { id =>
          replier(handlers.ask(FindUserById(id, _)), StatusCodes.OK)
        },
        get {
          onSuccess(reader.ask(AllUsers)) {
            case lur: List[Response.User] => respond(StatusCodes.OK, lur.asJson.toString())
            case _ => badRequest
          }
        },
        (post & path("login")) {
          entity(as[Request.Login]) { rl =>
            validated(rl, rl.rules) { valid =>
              onSuccess(handlers.ask(valid.asCmd)) {
                case ss: StatusReply[OAuthToken] if ss.isSuccess => respond(StatusCodes.OK, ss.getValue.asJson.toString())
                case ss: StatusReply[_] => respond(StatusCodes.BadRequest, RequestError(ss.getError.getMessage).asJson.toString())
                case _ => badRequest
              }
            }
          }
        }
      )
    }
  }
}
