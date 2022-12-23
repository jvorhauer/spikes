package spikes

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.behavior.{Handlers, Query, Reader}
import spikes.model._

import java.time.LocalDate
import java.util.UUID

class ApiTests extends AnyFlatSpec with Matchers with ScalaFutures with ScalatestRouteTest with BeforeAndAfterAll with TestUser {

  implicit val ts = system.toTyped
  val testKit = ActorTestKit(
    ConfigFactory.parseString(
      s"""akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
          akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
          akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID()}"
          akka.loggers = ["akka.event.Logging$$DefaultLogger"]
          akka.loglevel = DEBUG
      """
    )
  )

  val handlers: ActorRef[Command] = testKit.spawn(Handlers(), "api-test-handlers")
  val reader: ActorRef[Query] = testKit.spawn(Reader.query(), "query-handler")
  val route: Route = UserRoutes(handlers, reader).route

  "Post without User request" should "return bad request" in {
    Post("/users") ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.BadRequest
    }
  }

  "Post with invalid user request" should "return bad request with reasons" in {
    val rcu = Request.CreateUser("", "", "", LocalDate.now())
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.BadRequest
    }
  }

  "Post a valid User request" should "return a created user" in {
    val rcu = Request.CreateUser(name, email, password, born)
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      responseAs[Response.User].name shouldEqual name
    }
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Conflict
      responseAs[RequestError] shouldEqual RequestError("email already in use")
    }
  }

  "Create and Update User" should "return updated User" in {
    val rcu = Request.CreateUser("CreateAndUpdate", fakeEmail, password, born)
    var resp: Option[Response.User] = None
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      resp = Some(responseAs[Response.User])
    }
    resp.isDefined shouldBe true
    val ruu = Request.UpdateUser(resp.get.id, "Flipje", password, born)
    Put("/users", ruu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[Response.User].name shouldEqual "Flipje"
    }

    Get(s"/users/${resp.get.id}") ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[Response.User].name shouldEqual "Flipje"
    }
  }

  "Create and Delete User" should "return the deleted User" in {
    val eeeemail = "create.delete@test.er"
    val rcu = Request.CreateUser("CreateAndDelete", eeeemail, password, born)
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      responseAs[Response.User].name shouldEqual rcu.name
    }

    val rl = Request.Login(eeeemail, password)
    var resp: Option[OAuthToken] = None
    Post("/users/login", rl) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      resp = Some(responseAs[OAuthToken])
    }
    resp should not be None
    val token = resp.get.access_token

    val rdu = Request.DeleteUser(eeeemail)
    Delete("/users", rdu) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Accepted
      responseAs[Response.User].email shouldEqual rcu.email
    }
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()
}
