package spikes.route

import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.{PathMatcher, PathMatcher1, StandardRoute}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import wvlet.airframe.ulid.ULID

abstract class Router extends FailFastCirceSupport {

  val pULID: PathMatcher1[ULID] = PathMatcher("""[0-7][0-9A-HJKMNP-TV-Z]{25}""".r).map(ULID.fromString)
  val ok: StandardRoute = complete(StatusCodes.OK)
  val badRequest: StandardRoute = complete(StatusCodes.BadRequest)
  val serviceUnavailable: StandardRoute = complete(StatusCodes.ServiceUnavailable)
  val notFound: StandardRoute = complete(StatusCodes.NotFound)
}
