package spikes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import kamon.Kamon
import spikes.behavior.{Handlers, Reaper}
import spikes.route._
import spikes.validate.Validation

import scala.concurrent.duration.DurationInt


object Spikes {
  private def start(routes: Route)(implicit system: ActorSystem[Nothing]): Behavior[Nothing] = {
    Http(system).newServerAt("0.0.0.0", 8080).bind(routes)
    Behaviors.empty
  }

  def main(args: Array[String]): Unit = {
    Kamon.init()
    val guardian: Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      implicit val system = ctx.system
      val handlers = ctx.spawn(Handlers(), "handlers")
      ctx.spawn(Reaper(handlers, 1.minute), "reaper")
      val routes: Route = handleRejections(Validation.rejectionHandler) {
        concat(
          UserRouter(handlers).route,
          InfoRouter(handlers).route,
          TaskRouter(handlers).route)
      }
      start(routes)
    }
    ActorSystem[Nothing](guardian, "spikes")
  }
}
