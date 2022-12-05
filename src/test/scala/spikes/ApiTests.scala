package spikes

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.model.{RequestCreateUser, UserBehavior, UserInputError, UserResponse, UserRoutes}

import java.time.LocalDate

class ApiTests extends AnyFlatSpec with Matchers with ScalaFutures with ScalatestRouteTest with BeforeAndAfterAll {

  val name = "Tester"
  val email = "test@test.er"
  val password = "Welkom123!"
  val born = LocalDate.now().minusYears(21)

  implicit val ts = system.toTyped
  val testKit = ActorTestKit(
    ConfigFactory.parseString(
      """akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
         akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
         akka.persistence.snapshot-store.local.dir = "build/snapshot-${UUID.randomUUID()}"
         akka.loggers = ["akka.event.Logging$DefaultLogger"]
         akka.loglevel = DEBUG
      """
    )
  )

  val ub: ActorRef[Command] = testKit.spawn(UserBehavior())
  val route: Route = UserRoutes(ub).route

  "Post without User request" should "return bad request" in {
    Post("/users") ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.BadRequest
    }
  }

  "Post with invalid user request" should "return bad request with reasons" in {
    val rcu = RequestCreateUser("", "", "", LocalDate.now())
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.BadRequest
    }
  }

  "Post a valid User request" should "return a created user" in {
    val rcu = RequestCreateUser(name, email, password, born)
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      responseAs[UserResponse].name shouldEqual name
    }
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Conflict
      responseAs[UserInputError] shouldEqual UserInputError("email already in use")
    }
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()
}
