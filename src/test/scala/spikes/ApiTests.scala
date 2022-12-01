package spikes

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{MessageEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.model.RequestCreateUser
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import java.time.LocalDate

class ApiTests extends AnyFlatSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  val name = "Tester"
  val email = "test@test.er"
  val password = "Welkom123!"
  val born = LocalDate.now().minusYears(21)

  "Post without User request" should "return bad request" in {
    Post("/users") ~> Route.seal(Main.route) ~> check {
      status shouldEqual StatusCodes.BadRequest
    }
  }

  "Post with invalid user request" should "return bad request with reasons" in {
    val rcu = RequestCreateUser("", "", "", LocalDate.now())
    Post("/users", rcu) ~> Route.seal(Main.route) ~> check {
      status shouldEqual StatusCodes.BadRequest
    }
  }

  "Post a valid User request" should "return a created user" in {
    val rcu = RequestCreateUser(name, email, password, born)
    Post("/users", rcu) ~> Main.route ~> check {
      status shouldEqual StatusCodes.Created
    }
  }
}
