package spikes.route

import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.StatusCodes.{ BadRequest, OK }
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.build.BuildInfo
import spikes.model.{ Command, Respons }

import java.net.InetAddress
import scala.concurrent.ExecutionContextExecutor
import spikes.behavior.Handlers

case class InfoRouter(handlers: ActorRef[Command])(implicit system: ActorSystem[Nothing]) extends Router(handlers) {

  import InfoRouter.*

  implicit val ec: ExecutionContextExecutor = system.executionContext

  import akka.actor.typed.scaladsl.AskPattern.{ schedulerFromActorSystem, Askable }

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
  private val ia = InetAddress.getLocalHost
  private val hostname: String = ia.getHostName
  case class GetInfo(replyTo: ActorRef[StatusReply[Info]]) extends Command
  case class Info(
      users: Long,
      sessions: Int,
      tasks: Long,
      recovered: Boolean = false,
      host: String = hostname,
      version: String = BuildInfo.version,
      built: String = BuildInfo.buildTime,
      entityId: String = Handlers.pid.entityId
  ) extends Respons
}
