package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives.{pathEndOrSingleSlash, *}
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.model.{Command, OAuthToken, User}
import spikes.validate.Validation.validated

import scala.concurrent.Future

final case class UserRouter(manager: ActorRef[Command])(implicit system: ActorSystem[Nothing]) extends Router {

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

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
              onSuccess(lookup(up.id, User.key)) {
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
              User.Repository.find(id) match {
                case Some(us) => complete(StatusCodes.OK, User.Response(us).asJson)
                case None => notFound
              }
            },
            (path("me") & authenticateOAuth2Async("spikes", auth)) { us =>
              User.Repository.find(us.id) match {
                case Some(us) => complete(StatusCodes.OK, User.Response(us).asJson)
                case None => notFound
              }
            },
            (pathEndOrSingleSlash & parameters("start".as[Int].optional, "count".as[Int].optional)) { (start, count) =>
              complete(StatusCodes.OK, User.Repository.list(count.getOrElse(10), start.getOrElse(0)).map(User.Response(_)).asJson)
            }
          )
        },
        (post & path("login") & entity(as[User.Authenticate])) {
          validated(_) { ua =>
            onSuccess(lookup(ua.email, User.key)) {
              case oar: Option[ActorRef[Command]] => oar match {
                case Some(ar) => onSuccess(ar.ask(ua.asCmd)) {
                  case ss: StatusReply[OAuthToken] if ss.isSuccess => complete(StatusCodes.OK, ss.getValue.asJson)
                  case ss: StatusReply[OAuthToken] => complete(StatusCodes.BadRequest, RequestError(ss.getError.getMessage).asJson)
                  case xx => complete(StatusCodes.BlockedByParentalControls, RequestError(s"WTF? ${xx}"))
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
