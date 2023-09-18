package spikes.route

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import io.circe.generic.auto.*
import io.circe.syntax.*
import spikes.model.{Command, Tag}
import spikes.validate.Validation.validated

final case class TagRouter(ar: ActorRef[Command])(implicit system: ActorSystem[?]) extends Router {

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  val route: Route = pathPrefix("tags") {
    concat(
      (post & pathEndOrSingleSlash & authenticateOAuth2Async("spikes", auth)) { _ =>
        entity(as[Tag.Post]){
          validated(_) { tp =>
            onSuccess(ar.ask(tp.toCmd)) {
              case sur if sur.isSuccess => complete(StatusCodes.Created, sur.getValue.asJson)
              case sur                  => complete(StatusCodes.Conflict, RequestError(sur.getError.getMessage).asJson)
            }
          }
        }
      }
    )
  }
}
