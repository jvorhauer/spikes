package spikes.route

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.Directives.{pathEndOrSingleSlash, *}
import akka.http.scaladsl.server.Route
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.model.Session


case class SessionRouter()(implicit system: ActorSystem[?]) extends Router {

  val route: Route = pathPrefix("sessions") {
    (get & pathEndOrSingleSlash) {
      complete(OK, Session.list().map(Session.Response(_)).asJson)
    }
  }
}
