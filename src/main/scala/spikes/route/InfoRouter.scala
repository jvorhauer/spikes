package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes.{BadRequest, OK}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.behavior.Manager.{GetInfo, Info}
import spikes.model.Command

import scala.concurrent.ExecutionContext

final case class InfoRouter(manager: ActorRef[Command])(implicit system: ActorSystem[Nothing]) extends Router {

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  implicit val ec: ExecutionContext = system.executionContext

  val route: Route = get {
    concat(
      path("info") {
        complete(manager.ask(GetInfo).map {
          case info: StatusReply[Info] if info.isSuccess => OK -> info.getValue.asJson
          case _ => BadRequest -> None.asJson
        })
      },
      path("liveness") {
        complete(manager.ask(GetInfo).map {
          case info: StatusReply[Info] if info.isSuccess && info.getValue.recovered => OK -> info.getValue.asJson
          case _ => BadRequest -> None.asJson
        })
      },
      path("readiness") {
        complete(manager.ask(GetInfo).map {
          case info: StatusReply[Info] if info.isSuccess && info.getValue.recovered => OK -> info.getValue.asJson
          case _ => BadRequest -> None.asJson
        })
      },
    )
  }
}
