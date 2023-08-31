package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes.{BadRequest, OK, ServiceUnavailable}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.behavior.Manager.{Check, Checked, GetInfo, Info, IsReady}
import spikes.model.Command

final case class InfoRouter(manager: ActorRef[Command])(implicit system: ActorSystem[Nothing]) extends Router {

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  val route: Route = get {
    concat(
      path("info") {
        complete(manager.ask(GetInfo).map {
          case info: StatusReply[Info] if info.isSuccess => OK -> info.getValue.asJson
          case _ => BadRequest -> None.asJson
        })
      },
      path("liveness") {
        complete(manager.ask(IsReady).map {
          case info: StatusReply[Boolean] if info.isSuccess && info.getValue => OK -> ProbeResult("UP").asJson
          case _ => ServiceUnavailable -> None.asJson
        })
      },
      path("readiness") {
        complete(manager.ask(IsReady).map {
          case info: StatusReply[Boolean] if info.isSuccess && info.getValue => OK -> ProbeResult("UP").asJson
          case _ => ServiceUnavailable -> None.asJson
        })
      },
      path("check") {
        complete(manager.ask(Check).map {
          case c: StatusReply[Checked] if c.isSuccess && c.getValue.ok => OK -> ProbeResult("OK").asJson
          case _ => ServiceUnavailable -> None.asJson
        })
      }
    )
  }
}

final case class ProbeResult(status: String)
