package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.model.{Command, Respons}

import scala.concurrent.ExecutionContextExecutor

case class InfoRouter(handlers: ActorRef[Command])(implicit system: ActorSystem[Nothing]) extends Router(handlers) {

  import InfoRouter.*

  implicit val ec: ExecutionContextExecutor = system.executionContext

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  val route: Route = concat(
    (path("info") & get) {
      onSuccess(handlers.ask(GetInfo)) {
        case sr: StatusReply[Info] =>
          complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, sr.getValue.asJson.toString())))
        case _ => complete(StatusCodes.BadRequest)
      }
    },
    (path("liveness") & get) {
      onSuccess(handlers.ask(GetInfo)) {
        case info: StatusReply[Info] if info.isSuccess && info.getValue.recovered => complete(StatusCodes.OK)
        case _ => complete(StatusCodes.ServiceUnavailable)
      }
    },
    (path("readiness") & get) {
      onSuccess(handlers.ask(GetInfo)) {
        case info: StatusReply[Info] if info.isSuccess && info.getValue.recovered => complete(StatusCodes.OK)
        case _ => complete(StatusCodes.ServiceUnavailable)
      }
    }
  )
}

object InfoRouter {
  case class GetInfo(replyTo: ActorRef[StatusReply[Info]]) extends Command

  case class Info(users: Int, sessions: Int, tasks: Int, recovered: Boolean = false) extends Respons
}
