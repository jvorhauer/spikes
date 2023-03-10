package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.StatusReply
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._
import spikes.model._

import scala.concurrent.{ExecutionContextExecutor, Future}

case class TaskRouter(handlers: ActorRef[Command])(implicit system: ActorSystem[Nothing]) extends Router(handlers) {

  implicit val ec: ExecutionContextExecutor = system.executionContext

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  private def entryReplier(fut: Future[StatusReply[Task.Response]], sc: StatusCode) =
    onSuccess(fut) {
      case srre: StatusReply[Task.Response] if srre.isSuccess => respond(sc, srre.getValue.asJson.toString())
      case srre: StatusReply[Task.Response] => respond(StatusCodes.Conflict, RequestError(srre.getError.getMessage).asJson.toString())
      case _ => badRequest
    }

  val route: Route = {
    concat(
      (pathPrefix("tasks") & pathEndOrSingleSlash) {
        concat(
          post {
            authenticateOAuth2Async(realm = "spikes", authenticator) { us =>
              entity(as[Task.Post]) { rce =>
                entryReplier(handlers.ask(rce.asCmd(us.id, _)), StatusCodes.Created)
              }
            }
          },
          Directives.put {
            authenticateOAuth2Async(realm = "spikes", authenticator) { us =>
              entity(as[Task.Put]) { rut =>
                entryReplier(handlers.ask(rut.asCmd(us.id, _)), StatusCodes.OK)
              }
            }
          },
          delete {
            authenticateOAuth2Async(realm = "spikes", authenticator) { us =>
              entity(as[Task.Delete]) { rdt =>
                entryReplier(handlers.ask(rdt.asCmd(us.id, _)), StatusCodes.OK)
              }
            }
          }
        )
      }
    )
  }
}
