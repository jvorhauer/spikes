package spikes.route

import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.model.*
import spikes.validate.Validation.validated

import scala.concurrent.{ ExecutionContextExecutor, Future }

case class NoteRouter(handlers: ActorRef[Command])(implicit system: ActorSystem[Nothing]) extends Router(handlers) {

  implicit val ec: ExecutionContextExecutor = system.executionContext

  import akka.actor.typed.scaladsl.AskPattern.{ schedulerFromActorSystem, Askable }

  private def replier(fut: Future[Note.Reply], sc: StatusCode) =
    onSuccess(fut) {
      case srre: Note.Reply if srre.isSuccess => complete(sc, srre.getValue.asJson)
      case srre: Note.Reply                   => complete(StatusCodes.Conflict, RequestError(srre.getError.getMessage).asJson)
      case _                                  => badRequest
    }

  val route: Route =
    concat(
      pathPrefix("notes") {
        concat(
          pathEndOrSingleSlash {
            concat(
              post {
                authenticateOAuth2Async(realm = "spikes", authenticator) { us =>
                  entity(as[Note.Post]) {
                    validated(_) { rce =>
                      replier(handlers.ask(rce.asCmd(us.id, _)), StatusCodes.Created)
                    }
                  }
                }
              },
              put {
                authenticateOAuth2Async(realm = "spikes", authenticator) { us =>
                  entity(as[Note.Put]) {
                    validated(_) { rut =>
                      replier(handlers.ask(rut.asCmd(us.id, _)), StatusCodes.OK)
                    }
                  }
                }
              },
              delete {
                authenticateOAuth2Async(realm = "spikes", authenticator) { _ =>
                  entity(as[Note.Delete]) { rdt =>
                    replier(handlers.ask(rdt.asCmd), StatusCodes.OK)
                  }
                }
              }
            )
          },
          get {
            path(pULID) { id =>
              authenticateOAuth2Async(realm = "spikes", authenticator) { _ =>
                replier(handlers.ask(Note.Get(id).asCmd), StatusCodes.OK)
              }
            }
          }
        )
      }
    )
}
