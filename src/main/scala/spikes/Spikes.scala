package spikes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.*
import kamon.Kamon
import spikes.behavior.Manager
import spikes.route.*
import spikes.validate.Validation


object Spikes {
  def main(args: Array[String]): Unit = {
    Kamon.init()
    val guardian: Behavior[Nothing] = apply()
    ActorSystem[Nothing](guardian, "spikes")
  }

  def apply(port: Int = 8080): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
    implicit val system = ctx.system

    val manager = ctx.spawn(Manager(), "manager")
    val routes = handleRejections(Validation.rejectionHandler) {
      concat(
        ManagerRouter(manager).route,
        InfoRouter(manager).route
      )
    }
    Http(system).newServerAt("0.0.0.0", port).bind(routes)
    Behaviors.empty
  }
}
