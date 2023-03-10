package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import spikes.model._
import spikes.validate.Rules
import spikes.validate.Validation.validated
import wvlet.airframe.ulid.ULID

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try


case class UserRouter(handlers: ActorRef[Command])(implicit system: ActorSystem[Nothing]) extends Router(handlers) {

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  implicit val ec: ExecutionContextExecutor = system.executionContext
  implicit val ulidEncoder: Encoder[ULID] = Encoder.encodeString.contramap[ULID](_.toString())
  implicit val ulidDecoder: Decoder[ULID] = Decoder.decodeString.emapTry { str => Try(ULID.fromString(str)) }

  private def replier(fut: Future[StatusReply[User.Response]], sc: StatusCode) =
    onSuccess(fut) {
      case sur: StatusReply[User.Response] if sur.isSuccess => respond(sc, sur.getValue.asJson.toString())
      case sur: StatusReply[User.Response] => respond(StatusCodes.Conflict, RequestError(sur.getError.getMessage).asJson.toString())
      case _ => badRequest
    }

  private def repliers(fut: Future[StatusReply[List[User.Response]]], sc: StatusCode) =
    onSuccess(fut) {
      case sur: StatusReply[List[User.Response]] if sur.isSuccess => respond(sc, sur.getValue.asJson.toString())
      case sur: StatusReply[List[User.Response]] => respond(StatusCodes.Conflict, RequestError(sur.getError.getMessage).asJson.toString())
      case _ => badRequest
    }


  val route: Route =
    pathPrefix("users") {
      concat(
        (post & pathEndOrSingleSlash) {
          entity(as[User.Post]) {
            validated(_, Rules.createUser) { valid =>
              onSuccess(handlers.ask(valid.asCmd)) {
                case sur: StatusReply[User.Response] if sur.isSuccess => respondWithHeader(Location(s"/users/${sur.getValue.id}")) {
                  respond(StatusCodes.Created, sur.getValue.asJson.toString())
                }
                case sur: StatusReply[_] => respond(StatusCodes.Conflict, RequestError(sur.getError.getMessage).asJson.toString())
                case _ => badRequest
              }
            }
          }
        },
        put {
          authenticateOAuth2Async(realm = "spikes", authenticator) { _ =>
            entity(as[User.Put]) {
              validated(_, Rules.updateUser) { valid =>
                replier(handlers.ask(valid.asCmd), StatusCodes.OK)
              }
            }
          }
        },
        delete {
          authenticateOAuth2Async(realm = "spikes", authenticator) { _ =>
            entity(as[User.Delete]) { rdu =>
              validated(rdu, Rules.deleteUser) { valid =>
                replier(handlers.ask(replyTo = valid.asCmd), StatusCodes.Accepted)
              }
            }
          }
        },
        (get & path(pULID)) { id => replier(handlers.ask(User.Find(id, _)), StatusCodes.OK) },
        (get & pathEndOrSingleSlash) {
          repliers(handlers.ask(User.All), StatusCodes.OK)
        },
        (post & path("login")) {
          entity(as[User.ReqLogin]) { rl =>
            validated(rl, Rules.login) { valid =>
              onSuccess(handlers.ask(valid.asCmd)) {
                case ss: StatusReply[OAuthToken] if ss.isSuccess => respond(StatusCodes.OK, ss.getValue.asJson.toString())
                case ss: StatusReply[_] => respond(StatusCodes.BadRequest, RequestError(ss.getError.getMessage).asJson.toString())
                case _ => badRequest
              }
            }
          }
        },
        (put & path("logout")) {
          authenticateOAuth2Async(realm = "spikes", authenticator) { us =>
            onSuccess(handlers.ask(ref => User.Logout(us.token, ref))) {
              case sr: StatusReply[_] if sr.isSuccess => complete(StatusCodes.OK)
              case _ => complete(StatusCodes.BadRequest)
            }
          }
        }
      )
    }
}
