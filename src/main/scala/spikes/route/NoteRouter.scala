package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.model
import spikes.model.{Command, Note, User}
import spikes.validate.Validation.validated


final case class NoteRouter()(implicit system: ActorSystem[?]) extends Router {

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
                    case sur: StatusReply[Note.Response] if sur.isSuccess =>
                      complete(StatusCodes.Created, Seq(Location(s"/notes/${sur.getValue.slug}")), sur.getValue.asJson)
                    case sur: StatusReply[model.Note.Response] => complete(StatusCodes.Conflict, RequestError(sur.getError.getMessage).asJson)
                    case _ => badRequest
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
                    case sur: StatusReply[Note.Response] if sur.isSuccess => complete(StatusCodes.OK, sur.getValue.asJson)
                    case sur: StatusReply[Note.Response] => complete(StatusCodes.BadRequest, RequestError(sur.getError.getMessage).asJson)
                    case _ => badRequest
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
              complete(StatusCodes.OK, Note.Repository.list(us.id).map(_.asResponse).asJson)
            },
            path(pULID) { noteId =>
              Note.Repository.find(noteId) match {
                case Some(note) => complete(StatusCodes.OK, note.asResponse.asJson)
                case None => complete(StatusCodes.NotFound, RequestError(s"No note for $noteId").asJson)
              }
            },
            path(Segment) { slug =>
              Note.Repository.find(slug) match {
                case Some(note) => complete(StatusCodes.OK, note.asResponse.asJson)
                case None => complete(StatusCodes.NotFound, RequestError(s"No note for $slug").asJson)
              }
            },
          )
        },
        (delete & path(pULID)) { noteId =>
          authenticateOAuth2Async("spikes", auth) { us =>
            onSuccess(lookup(us.id, User.key)) {
              case oar: Option[ActorRef[Command]] => oar match {
                case Some(ar) => onSuccess(ar.ask(Note.Remove(noteId, us.id, _))) {
                  case sur: StatusReply[Note.Response] if sur.isSuccess => complete(StatusCodes.OK)
                  case sur: StatusReply[Note.Response] => complete(StatusCodes.BadRequest, RequestError(sur.getError.getMessage).asJson)
                  case _ => badRequest
                }
                case None => badRequest
              }
              case _ => badRequest
            }
          }
        }
      )
    }
}
