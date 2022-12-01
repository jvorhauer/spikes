package spikes

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RejectionHandler, ValidationRejection}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import spikes.model.{RequestCreateUser, UserRequest}
import spikes.validate.ModelValidation.validateModel
import spikes.validate.ModelValidationRejection

import scala.io.StdIn

object Main {

  private def responser(msg: String) =
    HttpResponse(BadRequest, entity = HttpEntity(msg).withContentType(ContentTypes.`application/json`))

  implicit def rejectionHandler = RejectionHandler.newBuilder()
    .handle { case mvr@ModelValidationRejection(_) =>
      complete(responser(mvr.fields.asJson.toString()))
    }.handle { case vr: ValidationRejection =>
    complete(responser(vr.message))
  }.result()


  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "spikes")
    Http().newServerAt("127.0.0.1", 8080).bindFlow(route)
    StdIn.readLine("Hit ENTER to exit")
    system.terminate()
  }

  val route = handleRejections(rejectionHandler) {
    pathPrefix("users") {
      post {
        entity(as[RequestCreateUser]) { rcu =>
          validateModel(rcu, UserRequest.rules) { valid =>
            complete(
              HttpResponse(StatusCodes.Created, entity = HttpEntity(ContentTypes.`application/json`, valid.asJson.toString()))
            )
          }
        }
      }
    }
  }
}
