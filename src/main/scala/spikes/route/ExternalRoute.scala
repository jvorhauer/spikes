package spikes.route

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.model.Command
import spikes.model.External
import wvlet.airframe.ulid.ULID

import scala.concurrent.ExecutionContextExecutor
import scala.util.Try
import scala.concurrent.Future
import akka.http.scaladsl.model.StatusCode

final case class ExternalRoute(handlers: ActorRef[Command])(implicit system: ActorSystem[Nothing]) extends Router(handlers) {

  import akka.actor.typed.scaladsl.AskPattern.{ schedulerFromActorSystem, Askable }

  implicit val ec: ExecutionContextExecutor = system.executionContext
  implicit val ulidEncoder: Encoder[ULID] = Encoder.encodeString.contramap[ULID](_.toString())
  implicit val ulidDecoder: Decoder[ULID] = Decoder.decodeString.emapTry(str => Try(ULID.fromString(str)))

  private def replier(fut: Future[StatusReply[External.Response]], sc: StatusCode) =
    onSuccess(fut) {
      case ser: StatusReply[External.Response] if ser.isSuccess => complete(sc, ser.getValue.asJson)
      case ser: StatusReply[External.Response]                  => complete(StatusCodes.Conflict, RequestError(ser.getError.getMessage).asJson)
      case _                                                    => badRequest
    }


  val route: Route = pathPrefix("ext") {
    concat(
      (post & pathEndOrSingleSlash) {
        entity(as[String]) { ext =>
          onSuccess(handlers.ask(External.Post(ext).asCmd)) {
            case ser: StatusReply[External.Response] if ser.isSuccess =>
              complete(StatusCodes.Created, Seq(Location(s"/ext/${ser.getValue.id}")), ser.getValue.asJson)
            case _: StatusReply[External.Response] => badRequest
          }
        }
      },
      get {
        path(pULID) { id =>
          replier(handlers.ask(External.Find(id, _)), StatusCodes.OK)
        }
      }
    )
  }
}
