package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives.{onSuccess, pathPrefix}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder}
import spikes.model.{Command, Note, User}
import spikes.validate.Validation.validated
import wvlet.airframe.ulid.ULID

import scala.concurrent.Future
import scala.util.Try

final case class NotesRouter()(implicit system: ActorSystem[?]) extends Router {

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  implicit val ulidEncoder: Encoder[ULID] = Encoder.encodeString.contramap[ULID](_.toString())
  implicit val ulidDecoder: Decoder[ULID] = Decoder.decodeString.emapTry(str => Try(ULID.fromString(str)))

  private def replier(fut: Future[StatusReply[Note.Response]], sc: StatusCode) = onSuccess(fut) {
    case sur: StatusReply[Note.Response] if sur.isSuccess => complete(sc, sur.getValue.asJson)
    case sur: StatusReply[Note.Response] => complete(StatusCodes.Conflict, RequestError(sur.getError.getMessage).asJson)
    case _ => badRequest
  }

  val route: Route =
    pathPrefix("notes") {
      concat(
        (post & pathEndOrSingleSlash & authenticateOAuth2Async("spikes", auth)) { us =>
          entity(as[Note.Post]) {
            validated(_) { np =>
              onSuccess(lookup(us.id.toString, User.key)) {
                case oar: Option[ActorRef[Command]] => oar match {
                  case Some(ar) => replier(ar.ask(np.asCmd), StatusCodes.Created)
                  case None => badRequest
                }
                case _ => badRequest
              }
            }
          }
        }

      )
    }
}
