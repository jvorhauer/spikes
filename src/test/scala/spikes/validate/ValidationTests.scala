package spikes.validate

import akka.http.scaladsl.server.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.model.Request
import spikes.validate.Validation.{FieldErrorInfo, ModelValidationRejection, validated}


class ValidationTests extends AnyFlatSpec with Matchers with ScalatestRouteTest {
  case class Book(title: String, author: String, pages: Int) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set.apply(
      if (title.isEmpty) Some(FieldErrorInfo("title", "title cannot be empty")) else None,
      if (author.length > 3) None else Some(FieldErrorInfo("author", "author must be longer than 3")),
      if (pages < 11) Some(FieldErrorInfo("pages", "page count must be greater than 10")) else None,
    ).flatten
  }

  val routes: Route = {
    pathPrefix("books") {
      post {
        entity(as[Book]) { book =>
          validated(book) { _ =>
            complete("ok")
          }
        }
      }
    }
  }

  "Invalid Book" should "return model validation rejection set" in {
    Post("/books", Book("", "", 5)) ~> routes ~> check {
      assert(rejection === ModelValidationRejection(Set(
        FieldErrorInfo("title", "title cannot be empty"),
        FieldErrorInfo("author", "author must be longer than 3"),
        FieldErrorInfo("pages", "page count must be greater than 10")
      )))
    }
  }
}
