package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes.*
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.model.{Command, Comment, Note, User}
import spikes.validate.Validation.validated


final class NoteRouter(implicit system: ActorSystem[?]) extends Router {

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  val route: Route =
    pathPrefix("notes") {
      concat(
        (post & pathEndOrSingleSlash & authenticateOAuth2Async("spikes", auth)) { us =>
          entity(as[Note.Post]) {
            validated(_) { np =>
              onSuccess(lookup(us.id, User.key)) {
                case oar: Option[ActorRef[Command]] => oar match {
                  case Some(ar) => onSuccess(ar.ask(np.asCmd(us.id, _))) {
                    case sur if sur.isSuccess => complete(Created, Seq(Location(s"/notes/${sur.getValue.slug}")), sur.getValue.asJson)
                    case sur => complete(Conflict, RequestError(sur.getError.getMessage).asJson)
                  }
                  case None => badRequest
                }
                case _ => badRequest
              }
            }
          }
        },
        (put & pathEndOrSingleSlash & authenticateOAuth2Async("spikes", auth)) { us =>
          entity(as[Note.Put]) {
            validated(_) { np =>
              onSuccess(lookup(us.id, np.id, Note.key)) {
                case oar: Option[ActorRef[Command]] => oar match {
                  case Some(ar) => onSuccess(ar.ask(np.asCmd)) {
                    case sur if sur.isSuccess => complete(OK, sur.getValue.asJson)
                    case sur => complete(BadRequest, RequestError(sur.getError.getMessage).asJson)
                  }
                  case None => notFound
                }
                case _ => badRequest
              }
            }
          }
        },
        get {
          concat(
            (path("mine") & authenticateOAuth2Async("spikes", auth)) { us =>
              complete(OK, Note.list(us.id).map(_.toResponse).asJson)
            },
            path(pTSID) { noteId =>
              Note.find(noteId) match {
                case Some(note) => complete(OK, note.toResponse.asJson)
                case None => complete(NotFound, RequestError(s"No note for $noteId").asJson)
              }
            },
            path(Segment) { slug =>
              Note.find(slug) match {
                case Some(note) => complete(OK, note.toResponse.asJson)
                case None => complete(NotFound, RequestError(s"No note for $slug").asJson)
              }
            },
          )
        },
        (delete & path(pTSID)) { noteId =>
          authenticateOAuth2Async("spikes", auth) { us =>
            onSuccess(lookup(us.id, User.key)) {
              case oar: Option[ActorRef[Command]] => oar match {
                case Some(ar) => onSuccess(ar.ask(Note.Remove(noteId, us.id, _))) {
                  case sur if sur.isSuccess => complete(OK)
                  case sur => complete(BadRequest, RequestError(sur.getError.getMessage).asJson)
                }
                case None => badRequest
              }
              case _ => badRequest
            }
          }
        },
        (post & path(pTSID / "comments")) { noteId =>
          authenticateOAuth2Async("spikes", auth) { us =>
            entity(as[Comment.Post]) {
              validated(_) { cp =>
                onSuccess(lookup(noteId, Note.key)) {
                  case oar: Option[ActorRef[Command]] => oar match {
                    case Some(ar) => onSuccess(ar.ask(cp.asCmd(us.id, _))) {
                      case sur if sur.isSuccess => complete(Created, sur.getValue.asJson)
                      case sur => complete(BadRequest, RequestError(sur.getError.getMessage).asJson)
                    }
                    case None => badRequest
                  }
                  case _ => badRequest
                }
              }
            }
          }
        }
      )
    }
}

object NoteRouter {
  def apply()(implicit system: ActorSystem[?]): NoteRouter = new NoteRouter()
}
