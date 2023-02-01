package spikes.validate

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.validate.Validation.validated


class ValidationTests extends AnyFlatSpec with Matchers with ScalatestRouteTest {
  case class Book(title: String, author: String, pages: Int)

  val titleRule: FieldRule[String] = FieldRule("title", (_: String).nonEmpty, "title cannot be empty")
  val authorRule: FieldRule[String] = FieldRule("author", (_: String).length > 3, "author must be longer than 3")
  val pagesRule: FieldRule[Int] = FieldRule("pages", (_: Int) > 10, "page count must be greater than 10")
  val failRule: FieldRule[String] = FieldRule("thingy", (_: String) == "oink", "fail")
  val rules: Set[FieldRule[Int with String]] = Set(titleRule, authorRule, pagesRule)

  val routes: Route =  {
    pathPrefix("books") {
      post {
        entity(as[Book]) { book =>
          validated(book, rules) { _ =>
            complete("ok")
          }
        }
      } ~ {
        put {
          entity(as[Book]) { book =>
            validated(book, Set(failRule)) { _ =>
              complete("ok")
            }
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

  "Valid Book with invalid rule" should "reject validation" in {
    Put("/books", Book("Nice book", "Scott Tiger", 42)) ~> routes ~> check {
      assert(rejection === ValidationRejection("No such field for validation: thingy"))
    }
  }
}
