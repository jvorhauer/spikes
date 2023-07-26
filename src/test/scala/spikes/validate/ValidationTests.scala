package spikes.validate

import akka.http.scaladsl.server.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.model.Request
import spikes.validate.Validation.{ErrorInfo, ModelValidationRejection, validated}

import java.time.LocalDate


class ValidationTests extends AnyFlatSpec with Matchers with ScalatestRouteTest {
  case class Book(title: String, author: String, pages: Int) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set(
      Validation.validate(titleRule(title), title, "title"),
      if (author.length > 3) None else Some(ErrorInfo("author", "author must be longer than 3")),
      if (pages < 11) Some(ErrorInfo("pages", "page count must be greater than 10")) else None,
    ).flatten
  }

  case class Improved(title: String, name: String, when: LocalDate) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set.apply(
      Validation.validate(titleRule(title), title, "title"),
      Validation.validate(nameRule(name), name, "name"),
      Validation.validate(bornRule(when), when, "when")
    ).flatten
  }

  case class Account(name: String, password: String) extends Request {
    override lazy val validated: Set[ErrorInfo] = Set.apply(
      Validation.validate(nameRule(name), name, "name"),
      Validation.validate(passwordRule(password), password, "password")
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
        ErrorInfo("title", "\"\" is not a valid title"),
        ErrorInfo("author", "author must be longer than 3"),
        ErrorInfo("pages", "page count must be greater than 10")
      )))
    }
  }

  "Improved" should "validate" in {
    val when = LocalDate.now.minusYears(23)

    Improved("title", "name", when).validated should have size 0
    Improved("", "name", when).validated should have size 1
    Improved("", "", when).validated should have size 2
    Improved("", "", LocalDate.now).validated should have size 3

    Improved("title", "Howdy 2", when).validated should have size 1
  }

  "Password" should "validate" in {
    Account("name", "Welkom123!").validated should have size 0
    Account("nämé", "WasDah5#$").validated should have size 0
  }
}
