package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.{Route, RouteResult}
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LogEntry}
import akka.pattern.StatusReply
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder}
import spikes.model.*
import spikes.validate.Validation.validated
import wvlet.airframe.ulid.ULID

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

case class UserRouter(handlers: ActorRef[Command])(implicit system: ActorSystem[Nothing]) extends Router(handlers) {

  import akka.actor.typed.scaladsl.AskPattern.{ schedulerFromActorSystem, Askable }

  implicit val ec: ExecutionContextExecutor = system.executionContext
  implicit val ulidEncoder: Encoder[ULID] = Encoder.encodeString.contramap[ULID](_.toString())
  implicit val ulidDecoder: Decoder[ULID] = Decoder.decodeString.emapTry(str => Try(ULID.fromString(str)))

  private val logger: LoggingAdapter = system.classicSystem.log

  private def replier(fut: Future[StatusReply[User.Response]], sc: StatusCode) =
    onSuccess(fut) {
      case sur: StatusReply[User.Response] if sur.isSuccess => complete(sc, sur.getValue.asJson)
      case sur: StatusReply[User.Response]                  => complete(StatusCodes.Conflict, RequestError(sur.getError.getMessage).asJson)
      case _                                                => badRequest
    }

  private def repliers(fut: Future[StatusReply[List[User.Response]]], sc: StatusCode) =
    onSuccess(fut) {
      case sur: StatusReply[List[User.Response]] if sur.isSuccess => complete(sc, sur.getValue.asJson)
      case sur: StatusReply[List[User.Response]]                  => complete(StatusCodes.Conflict, RequestError(sur.getError.getMessage).asJson)
      case _                                                      => badRequest
    }

  private val rejectionLogger: HttpRequest => RouteResult => Option[LogEntry] = req => {
    case RouteResult.Rejected(rejections) => Some(LogEntry(s"Request ${req.entity} was rejected:\n$rejections", Logging.ErrorLevel))
    case RouteResult.Complete(response)   => Some(LogEntry(s"Request ${req.entity} was completed: ${response.status}", Logging.InfoLevel))
  }

  val route: Route =
    pathPrefix("users") {
      concat(
        (post & pathEndOrSingleSlash) {
          entity(as[User.Post]) {
            validated(_) { up =>
              onSuccess(handlers.ask(up.asCmd)) {
                case sur: StatusReply[User.Response] if sur.isSuccess =>
                  complete(StatusCodes.Created, Seq(Location(s"/users/${sur.getValue.id}")), sur.getValue.asJson)
                case sur: StatusReply[User.Response] => complete(StatusCodes.Conflict, RequestError(sur.getError.getMessage).asJson)
                case _                               => badRequest
              }
            }
          }
        },
        put {
          authenticateOAuth2Async(realm = "spikes", authenticator) { _ =>
            entity(as[User.Put]) {
              validated(_) { up =>
                replier(handlers.ask(up.asCmd), StatusCodes.OK)
              }
            }
          }
        },
        delete {
          authenticateOAuth2Async(realm = "spikes", authenticator) { _ =>
            entity(as[User.Delete]) {
              validated(_) { ud =>
                replier(handlers.ask(replyTo = ud.asCmd), StatusCodes.Accepted)
              }
            }
          }
        },
        get {
          concat(
            path(pULID) { id =>
              replier(handlers.ask(User.Find(id, _)), StatusCodes.OK)
            },
            path("me") {
              authenticateOAuth2Async(realm = "spikes", authenticator) { us =>
                replier(handlers.ask(User.Find(us.id, _)), StatusCodes.OK)
              }
            },
            pathEndOrSingleSlash(repliers(handlers.ask(User.All), StatusCodes.OK))
          )
        },
        (post & path("login")) {
          withLog(logger) {
            DebuggingDirectives.logRequestResult(rejectionLogger) {
              entity(as[User.Authenticate]) { li =>
                validated(li) { ul =>
                  onSuccess(handlers.ask(ul.asCmd)) {
                    case ss: StatusReply[OAuthToken] if ss.isSuccess => complete(StatusCodes.OK, ss.getValue.asJson)
                    case ss: StatusReply[?] => complete(StatusCodes.BadRequest, RequestError(ss.getError.getMessage).asJson)
                    case xx => complete(StatusCodes.BadRequest, RequestError(s"WTF? ${xx}"))
                  }
                }
              }
            }
          }
        },
        (put & path("logout")) {
          authenticateOAuth2Async(realm = "spikes", authenticator) { us =>
            onSuccess(handlers.ask(User.Logout(us.token, _))) {
              case sr: StatusReply[?] if sr.isSuccess => complete(StatusCodes.OK)
              case _                                  => complete(StatusCodes.BadRequest)
            }
          }
        },
        (post & path("follow")) {
          authenticateOAuth2Async(realm = "spikes", authenticator) { us =>
            entity(as[User.RequestFollow]) { rf =>
              onSuccess(handlers.ask(User.Follow(us.id, rf.other, _))) {
                case sr: StatusReply[?] if sr.isSuccess => complete(StatusCodes.OK)
                case _                                  => complete(StatusCodes.BadRequest)
              }
            }
            complete(StatusCodes.OK)
          }
        }
      )
    }
}
