package spikes.route

import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.{AsyncAuthenticator, complete}
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.{PathMatcher, PathMatcher1, StandardRoute}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.hypersistence.tsid.TSID
import spikes.behavior.Manager
import spikes.model.{Command, Session}

import scala.concurrent.{ExecutionContextExecutor, Future}

abstract class Router(implicit val system: ActorSystem[Nothing]) extends FailFastCirceSupport {

  implicit val ec: ExecutionContextExecutor = system.executionContext

  val pTSID: PathMatcher1[TSID] = PathMatcher("""[0-9A-HJKMNP-TV-Z]{13}""".r).map(TSID.from)

  val badRequest: StandardRoute = complete(StatusCodes.BadRequest)
  val notFound: StandardRoute = complete(StatusCodes.NotFound)

  type LookupResult = Future[Option[ActorRef[Command]]]

  def lookup(str: String, key: ServiceKey[Command])         : LookupResult = Manager.lookup(str, key)
  def lookup(id: TSID, key: ServiceKey[Command])            : LookupResult = lookup(id.toString, key)
  def lookup(id1: TSID, id2: TSID, key: ServiceKey[Command]): LookupResult = lookup(s"$id1-$id2", key)

  val auth: AsyncAuthenticator[Session] = {
    case Credentials.Provided(token) =>
      Session.find(token) match {
        case Some(us) if us.isValid(token) => Future.successful(Some(us))
        case _ => Future.successful(None)
      }
    case _ => Future.successful(None)
  }
}
