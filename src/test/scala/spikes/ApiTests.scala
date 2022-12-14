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
import spikes.behavior.Handlers
import spikes.model._

import java.time.LocalDate

class ApiTests extends AnyFlatSpec with Matchers with ScalaFutures with ScalatestRouteTest with BeforeAndAfterAll with TestUser {

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

  val ub: ActorRef[Command] = testKit.spawn(Handlers(), "api-test-handlers")
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

  "Create and Update User" should "return updated User" in {
    val rcu = RequestCreateUser("CreateAndUpdate", fakeEmail, password, born)
    var resp: Option[UserResponse] = None
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      resp = Some(responseAs[UserResponse])
    }
    resp.isDefined shouldBe true
    val ruu = RequestUpdateUser(resp.get.id, "Flipje", "flipje@test.er", password, born)
    Put("/users", ruu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[UserResponse].name shouldEqual "Flipje"
    }

    Get(s"/users/${resp.get.id}") ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[UserResponse].name shouldEqual "Flipje"
    }
  }

  "Create and Delete User" should "return the deleted User" in {
    val eeeemail = "create.delete@test.er"
    val rcu = RequestCreateUser("CreateAndDelete", eeeemail, password, born)
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      responseAs[UserResponse].name shouldEqual rcu.name
    }
    val rdu = RequestDeleteUser(eeeemail)
    Delete("/users", rdu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Accepted
      responseAs[UserResponse].email shouldEqual rcu.email
    }
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()
}
