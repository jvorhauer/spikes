package spikes.model

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.{PathMatcher, PathMatcher1}
import akka.pattern.StatusReply
import akka.util.Timeout
import wvlet.airframe.ulid.ULID
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import io.circe.syntax._
import spikes.validate.Rules
import spikes.validate.Validation.validated

import java.time.LocalDateTime
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Try
import akka.http.scaladsl.server.Route
import scala.concurrent.ExecutionContextExecutor

object Status extends Enumeration {
  type Status = Value
  val Blank, ToDo, Doing, Completed = Value
}
import Status._

final case class Entry(
  id: ULID, owner: ULID, title: String, body: String,
  status: Status = Status.Blank,
  url: Option[String] = None,
  due: Option[LocalDateTime] = None,
  starts: Option[LocalDateTime] = None, ends: Option[LocalDateTime] = None,
  tags: Set[Tag] = Set.empty, comments: Set[Comment] = Set.empty
) extends Entity with Ordered[Entry] {
  lazy val isTask: Boolean = due.isDefined && status != Status.Blank
  lazy val isEvent: Boolean = starts.isDefined && ends.isDefined
  lazy val isMarker: Boolean = url.isDefined
  lazy val written: LocalDateTime = created
  override def compare(that: Entry): Int = if (this.id == that.id) 0 else if (this.id > that.id) 1 else -1
}


final case class EntryRouter(handlers: ActorRef[Command])(implicit system: ActorSystem[_]) {

  private val jsont = ContentTypes.`application/json`

  implicit val ec: ExecutionContextExecutor = system.executionContext
  implicit val timeout: Timeout = 3.seconds
  implicit val ulidEncoder: Encoder[ULID] = Encoder.encodeString.contramap[ULID](_.toString())
  implicit val ulidDecoder: Decoder[ULID] = Decoder.decodeString.emapTry { str => Try(ULID.fromString(str)) }
  implicit val statusEncoder: Encoder[Status.Value] = Encoder.encodeEnumeration(Status)
  implicit val statusDecoder: Decoder[Status.Value] = Decoder.decodeEnumeration(Status)

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  private val authenticator: AsyncAuthenticator[UserSession] = {
    case Credentials.Provided(token) => handlers.ask(Command.Authenticate(token, _))
    case _ => Future.successful(None)
  }

  private def respond(sc: StatusCode, body: String) = complete(HttpResponse(sc, entity = HttpEntity(jsont, body)))
  private val badRequest = complete(StatusCodes.BadRequest)
  private def replier(fut: Future[StatusReply[Response.Entry]], sc: StatusCode) =
    onSuccess(fut) {
      case sur: StatusReply[Response.Entry] if sur.isSuccess => respond(sc, sur.getValue.asJson.toString())
      case sur: StatusReply[Response.Entry] => respond(StatusCodes.Conflict, RequestError(sur.getError.getMessage).asJson.toString())
      case _ => badRequest
    }


  val pULID: PathMatcher1[ULID] = PathMatcher("""[A-HJKMNP-TV-Z0-9]{26}""".r).map(ULID.fromString)

  val route: Route =
    pathPrefix("entries") {
      concat(
        (post & pathEndOrSingleSlash) {
          authenticateOAuth2Async(realm = "spikes", authenticator) { us =>
            entity(as[Request.CreateEntry]) { rce =>
              validated(rce, Rules.createEntry) { valid =>
                replier(handlers.ask(rt => valid.asCmd(us.id, rt)), StatusCodes.Created)
              }
            }
          }
        }
      )
    }
}
