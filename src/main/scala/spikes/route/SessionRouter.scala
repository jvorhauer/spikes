package spikes.route

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.Directives.{pathEndOrSingleSlash, *}
import akka.http.scaladsl.server.Route
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.model.Session


final class SessionRouter(implicit system: ActorSystem[?]) extends Router {

  val route: Route = pathPrefix("sessions") {
    (get & pathEndOrSingleSlash) {
      complete(OK, Session.list().map(Session.Response(_)).asJson)
    }
  }
}

object SessionRouter {
  def apply()(implicit system: ActorSystem[?]): SessionRouter = new SessionRouter()
}
