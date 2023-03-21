package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes.{BadRequest, OK}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.build.BuildInfo
import spikes.model.{Command, Respons}

import scala.concurrent.ExecutionContextExecutor

case class InfoRouter(handlers: ActorRef[Command])(implicit system: ActorSystem[Nothing]) extends Router(handlers) {

  import InfoRouter.*

  implicit val ec: ExecutionContextExecutor = system.executionContext

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  val route: Route = concat(
    (path("info") & get) {
      complete(handlers.ask(GetInfo).map {
        case info: StatusReply[Info] if info.isSuccess => OK -> info.getValue.asJson
        case _                                         => BadRequest -> None.asJson
      })
    },
    (path("liveness") & get) {
      complete(handlers.ask(GetInfo).map {
        case info: StatusReply[Info] if info.isSuccess && info.getValue.recovered => OK -> info.getValue.asJson
        case _                                                                    => BadRequest -> None.asJson
      })
    },
    (path("readiness") & get) {
      complete(handlers.ask(GetInfo).map {
        case info: StatusReply[Info] if info.isSuccess && info.getValue.recovered => OK -> info.getValue.asJson
        case _                                                                    => BadRequest -> None.asJson
      })
    }
  )
}

object InfoRouter {
  case class GetInfo(replyTo: ActorRef[StatusReply[Info]]) extends Command
  case class Info(
      users: Long,
      sessions: Int,
      tasks: Long,
      bookmarks: Long,
      recovered: Boolean = false,
      build: String = s"${BuildInfo.version} @ ${BuildInfo.buildTime} (${BuildInfo.scalaVersion})"
  ) extends Respons
}
