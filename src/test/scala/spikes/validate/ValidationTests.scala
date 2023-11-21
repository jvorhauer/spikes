package spikes.validate

import akka.http.scaladsl.server.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.model.{Request, ValidatableString}
import spikes.validate.ValidationTests.{AuthorError, PagesError, TitleError}
import spikes.validate.Validator.{ErrorInfo, RequestValidationRejection, ValidationError, validated}

import scala.util.matching.Regex


class ValidationTests extends AnyFlatSpec with Matchers with ScalatestRouteTest {

  val rtitle: Regex = "^[\\p{L}\\s\\d\\W]{1,255}$".r

  case class Book(title: String, author: String, pages: Int) extends Request {
    override def validated: Validated[ValidationError, Book] = Validator(this)
      .satisfying(_.title.matches(rtitle), TitleError(value = this.title))
      .satisfying(_.author.length > 3, AuthorError(value = this.author))
      .satisfying(_.pages > 11, PagesError(value = this.pages.toString))
      .applied
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
      assert(rejection === RequestValidationRejection(List(
        ErrorInfo("Book.title", "invalid: <empty>"),
        ErrorInfo("Book.author", "invalid: <empty>"),
        ErrorInfo("Book.pages", "invalid: 5")
      )))
    }
  }
}

object ValidationTests {
  sealed trait BookValidationError extends ValidationError {
    override def entity: String = "Book"
  }
  final case class TitleError(field: String = "title", value: String) extends BookValidationError
  final case class AuthorError(field: String = "author", value: String) extends BookValidationError
  final case class PagesError(field: String = "pages", value: String) extends BookValidationError
}
