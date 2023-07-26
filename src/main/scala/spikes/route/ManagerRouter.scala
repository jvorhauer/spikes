package spikes.route

import akka.actor.typed.receptionist.Receptionist.Find
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.pattern.StatusReply
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder}
import spikes.model.{Command, OAuthToken, User}
import spikes.validate.Validation.validated
import wvlet.airframe.ulid.ULID

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

final case class ManagerRouter(manager: ActorRef[Command])(implicit system: ActorSystem[Nothing]) extends Router {

  type LookupResult = Future[Option[ActorRef[Command]]]

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  implicit val ec: ExecutionContextExecutor = system.executionContext
  implicit val ulidEncoder: Encoder[ULID] = Encoder.encodeString.contramap[ULID](_.toString())
  implicit val ulidDecoder: Decoder[ULID] = Decoder.decodeString.emapTry(str => Try(ULID.fromString(str)))

  private val receptionist = system.receptionist

  private def lookup(str: String): LookupResult = receptionist.ask(Find(User.key)).map(_.serviceInstances(User.key).find(_.path.name.contains(str)))

  private val auth: AsyncAuthenticator[User.Session] = {
    case Credentials.Provided(token) =>
      lookup(token).flatMap {
        case Some(ar) => ar.ask(User.Authorize(token, _))
        case None     => Future.successful(None)
      }
    case _ => Future.successful(None)
  }


  private def replier(fut: Future[StatusReply[User.Response]], sc: StatusCode) = onSuccess(fut) {
    case sur: StatusReply[User.Response] if sur.isSuccess => complete(sc, sur.getValue.asJson)
    case sur: StatusReply[User.Response] => complete(StatusCodes.Conflict, RequestError(sur.getError.getMessage).asJson)
    case _ => badRequest
  }

  val route: Route =
    pathPrefix("users") {
      concat(
        (post & pathEndOrSingleSlash & entity(as[User.Post])) {
          validated(_) { up =>
            onSuccess(manager.ask(up.asCmd)) {
              case sur: StatusReply[User.Response] if sur.isSuccess =>
                complete(StatusCodes.Created, Seq(Location(s"/users/${sur.getValue.id}")), sur.getValue.asJson)
              case sur: StatusReply[User.Response] => complete(StatusCodes.Conflict, RequestError(sur.getError.getMessage).asJson)
              case _ => badRequest
            }
          }
        },
        (put & pathEndOrSingleSlash & authenticateOAuth2Async("spikes", auth)) { _ =>
          entity(as[User.Put]) {
            validated(_) { up =>
              onSuccess(lookup(up.id.toString)) {
                case oar: Option[ActorRef[Command]] => oar match {
                  case Some(ar) => replier(ar.ask(up.asCmd), StatusCodes.OK)
                  case None => badRequest
                }
                case _ => badRequest
              }
            }
          }
        },
        (delete & pathEndOrSingleSlash & authenticateOAuth2Async("spikes", auth)) { us =>
          replier(manager.ask(User.Remove(us.id, _)), StatusCodes.Accepted)
        },
        get {
          concat(
            path(pULID) { id =>
              onSuccess(lookup(id.toString)) {
                case oar: Option[ActorRef[Command]] => oar match {
                  case Some(ar) => replier(ar.ask(User.Find(id, _)), StatusCodes.OK)
                  case None => notFound
                }
                case _ => notFound
              }
            }
          )
        },
        (post & path("login") & entity(as[User.Authenticate])) {
          validated(_) { ua =>
            onSuccess(lookup(ua.email)) {
              case oar: Option[ActorRef[Command]] => oar match {
                case Some(ar) => onSuccess(ar.ask(ua.asCmd)) {
                  case ss: StatusReply[OAuthToken] if ss.isSuccess => complete(StatusCodes.OK, ss.getValue.asJson)
                  case ss: StatusReply[?] => complete(StatusCodes.BadRequest, RequestError(ss.getError.getMessage).asJson)
                  case xx => complete(StatusCodes.BadRequest, RequestError(s"WTF? ${xx}"))
                }
                case None => notFound
              }
              case _ => notFound
            }
          }
        }
      )
    }
}
