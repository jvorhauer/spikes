package spikes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.*
import kamon.Kamon
import spikes.behavior.{Handlers, Reaper}
import spikes.route.*
import spikes.validate.Validation

import scala.concurrent.duration.DurationInt


object Spikes {
  def main(args: Array[String]): Unit = {
    Kamon.init()
    val guardian: Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>

      implicit val system = ctx.system

      val handlers = ctx.spawn(Handlers(), "handlers")
      ctx.spawn(Reaper(handlers, 1.minute), "reaper")
      val routes = handleRejections(Validation.rejectionHandler) {
        concat(
          UserRouter(handlers).route,
          InfoRouter(handlers).route,
          TaskRouter(handlers).route,
          BookmarkRouter(handlers).route
        )
      }
      Http(ctx.system).newServerAt("0.0.0.0", 8080).bind(routes)
      Behaviors.empty
    }
    ActorSystem[Nothing](guardian, "spikes")
  }
}
