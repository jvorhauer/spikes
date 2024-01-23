package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes.{BadRequest, OK, ServiceUnavailable}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.behavior.Manager.{Check, Checked, GetInfo, GetStati, Info, IsReady, StatusValues}
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
          case info: StatusReply[Boolean] if info.isSuccess && info.getValue => OK -> jsonify("status", "UP")
          case _ => ServiceUnavailable -> None.asJson
        })
      },
      path("readiness") {
        complete(manager.ask(IsReady).map {
          case info: StatusReply[Boolean] if info.isSuccess && info.getValue => OK -> jsonify("status", "UP")
          case _ => ServiceUnavailable -> None.asJson
        })
      },
      path("check") {
        complete(manager.ask(Check).map {
          case c: StatusReply[Checked] if c.isSuccess && c.getValue.ok => OK -> jsonify("result", "OK")
          case _ => ServiceUnavailable -> None.asJson
        })
      },
      path("stati"){
        complete(manager.ask(GetStati).map {
          case c: StatusReply[StatusValues] if c.isSuccess => OK -> c.getValue.stati.asJson
          case _ => ServiceUnavailable -> None.asJson
        })
      }
    )
  }

  private val jsonify: (String, String) => Json = (key: String, value: String) => Json.obj((key, Json.fromString(value)))
}
