package spikes.validate

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.wix.accord.ResultBuilders
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.validate.ModelValidation.validateModel


class ValidationTests extends AnyFlatSpec with ResultBuilders with Matchers with ScalatestRouteTest {
  case class Book(title: String, author: String, pages: Int)

  val titleRule = FieldRule("title", (_: String).nonEmpty, "title cannot be empty")
  val authorRule = FieldRule("author", (_: String).length > 3, "author must be longer than 3")
  val pagesRule = FieldRule("pages", (_: Int) > 10, "page count must be greater than 10")
  val failRule = FieldRule("thingy", (_: String) == "oink", "fail")
  val rules = Set(titleRule, authorRule, pagesRule)

  private def responser(msg: String) =
    HttpResponse(BadRequest, entity = HttpEntity(msg).withContentType(`application/json`))

  def rejectionHandler = RejectionHandler.newBuilder()
    .handle { case mvr @ ModelValidationRejection(_) =>
      complete(responser(mvr.fields.asJson.toString()))
    }.handle { case vr: ValidationRejection =>
      complete(responser(vr.message))
    }.result()

  val routes =  {
    pathPrefix("books") {
      post {
        entity(as[Book]) { book =>
          validateModel(book, rules) { _ =>
            complete("ok")
          }
        }
      } ~ {
        put {
          entity(as[Book]) { book =>
            validateModel(book, Set(failRule)) { _ =>
              complete("ok")
            }
          }
        }
      }
    }
  }

  "Validation Test" should "reject" in {
    Post("/books", Book("", "", 5)) ~> routes ~> check {
      assert(rejection === ModelValidationRejection(Set(
        FieldErrorInfo("title", "title cannot be empty"),
        FieldErrorInfo("author", "author must be longer than 3"),
        FieldErrorInfo("pages", "page count must be greater than 10")
      )))
    }
  }

  "Invalid Test" should "not complete" in {
    Put("/books", Book("Nice book", "Scott Tiger", 42)) ~> routes ~> check {
      assert(rejection === ValidationRejection("No such field for validation: thingy"))
    }
  }
}
