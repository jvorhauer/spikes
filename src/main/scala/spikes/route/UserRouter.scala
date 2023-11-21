package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes.*
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.model.*
import spikes.validate.Validator.validated

import scala.concurrent.Future

final case class UserRouter(manager: ActorRef[Command])(implicit system: ActorSystem[Nothing]) extends Router {

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  private def replier(fut: Future[StatusReply[User.Response]], sc: StatusCode) = onSuccess(fut) {
    case sur: StatusReply[User.Response] if sur.isSuccess => complete(sc, sur.getValue.asJson)
    case sur: StatusReply[User.Response] => complete(Conflict, RequestError(sur.getError.getMessage).asJson)
  }

  val route: Route =
    pathPrefix("users") {
      concat(
        (post & pathEndOrSingleSlash & entity(as[User.Post])) {
          validated(_) { up =>
            onSuccess(manager.ask(up.asCmd)) {
              case sur if sur.isSuccess => complete(Created, Seq(Location(s"/users/${sur.getValue.id}")), sur.getValue.asJson)
              case sur                  => complete(Conflict, RequestError(sur.getError.getMessage).asJson)
            }
          }
        },
        (put & pathEndOrSingleSlash & authenticateOAuth2Async("spikes", auth)) { _ =>
          entity(as[User.Put]) {
            validated(_) { up =>
              onSuccess(lookup(up.id, User.key)) {
                case oar: Option[ActorRef[Command]] => oar match {
                  case Some(ar) => replier(ar.ask(up.asCmd), OK)
                  case None => badRequest
                }
                case _ => badRequest
              }
            }
          }
        },
        (delete & pathEndOrSingleSlash & authenticateOAuth2Async("spikes", auth)) { us =>
          onSuccess(manager.ask(User.Remove(us.id, _))) {
            case sur if sur.isSuccess => complete(OK)
            case sur => complete(BadRequest, RequestError(sur.getError.getMessage).asJson)
          }
        },
        get {
          concat(
            path(pTSID) { id =>
              User.find(id) match {
                case Some(us) => complete(OK, User.Response(us).asJson)
                case None => notFound
              }
            },
            (path("me") & authenticateOAuth2Async("spikes", auth)) { us =>
              User.find(us.id) match {
                case Some(us) => complete(OK, User.Response(us).asJson)
                case None => notFound
              }
            },
            (pathEndOrSingleSlash & parameters("start".as[Int].optional, "count".as[Int].optional)) { (start, count) =>
              complete(OK, User.list(count.getOrElse(10), start.getOrElse(0)).map(User.Response(_)).asJson)
            }
          )
        },
        (post & path("login") & entity(as[User.Authenticate])) {
          validated(_) { ua =>
            onSuccess(lookup(ua.email, User.key)) {
              case oar: Option[ActorRef[Command]] => oar match {
                case Some(ar) => onSuccess(ar.ask(ua.asCmd)) {
                  case ss if ss.isSuccess => complete(OK, ss.getValue.asJson)
                  case ss                 => complete(BadRequest, RequestError(ss.getError.getMessage).asJson)
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
