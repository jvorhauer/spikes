package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.{AsyncAuthenticator, complete}
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.{PathMatcher, PathMatcher1, StandardRoute}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import spikes.model.{Command, User}
import wvlet.airframe.ulid.ULID

import scala.concurrent.Future

abstract class Router(handlers: ActorRef[Command])(implicit val system: ActorSystem[?]) extends FailFastCirceSupport {

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  val authenticator: AsyncAuthenticator[User.Session] = {
    case Credentials.Provided(token) => handlers.ask(User.Authorize(token, _))
    case _ => Future.successful(None)
  }

  val pULID: PathMatcher1[ULID] = PathMatcher("""[A-HJKMNP-TV-Z0-9]{26}""".r).map(ULID.fromString)

  val ok: StandardRoute = complete(StatusCodes.OK)
  val badRequest: StandardRoute = complete(StatusCodes.BadRequest)
  val serviceUnavailable: StandardRoute = complete(StatusCodes.ServiceUnavailable)
}
