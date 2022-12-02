package spikes

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import spikes.model.{RequestCreateUser, UserRequest}
import spikes.validate.ModelValidation
import spikes.validate.ModelValidation.validated

import scala.io.StdIn

object Main {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "spikes")
    Http().newServerAt("127.0.0.1", 8080).bindFlow(route)
    StdIn.readLine("Hit <Return> to exit")
    system.terminate()
  }

  val route = handleRejections(ModelValidation.rejectionHandler) {
    pathPrefix("users") {
      post {
        entity(as[RequestCreateUser]) { rcu =>
          validated(rcu, UserRequest.rules) { valid =>
            complete(
              HttpResponse(StatusCodes.Created, entity = HttpEntity(ContentTypes.`application/json`, valid.asJson.toString()))
            )
          }
        }
      }
    }
  }
}
