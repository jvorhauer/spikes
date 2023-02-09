package spikes.api

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.{Decoder, Encoder}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.InfoRouter
import spikes.behavior.{Finder, Handlers, Query, Reader}
import spikes.model._
import spikes.validate.Validation
import wvlet.airframe.ulid.ULID

import java.time.LocalDate
import java.util.UUID
import scala.util.Try
import akka.actor.typed.ActorSystem
import spikes.db.Repository

class UsersTests extends AnyFlatSpec with Matchers with ScalaFutures with ScalatestRouteTest with BeforeAndAfterAll with BeforeAndAfterEach with TestUser {

  implicit val ulidEncoder: Encoder[ULID] = Encoder.encodeString.contramap[ULID](_.toString())
  implicit val ulidDecoder: Decoder[ULID] = Decoder.decodeString.emapTry { str => Try(ULID.fromString(str)) }

  implicit val ts: ActorSystem[Nothing] = system.toTyped
  val testKit: ActorTestKit = ActorTestKit(
    ConfigFactory.parseString(
      s"""akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
          akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
          akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID()}"
          akka.loggers = ["akka.event.Logging$$DefaultLogger"]
          akka.loglevel = DEBUG
      """
    )
  )

  val finder: ActorRef[Event] = testKit.spawn(Finder(), "api-test-finder")
  val handlers: ActorRef[Command] = testKit.spawn(Handlers(findr = finder), "api-test-handlers")
  val querier: ActorRef[Query] = testKit.spawn(Reader.query(), "query-handler")
  val route: Route = handleRejections(Validation.rejectionHandler) {
    concat(UserRouter(handlers, querier).route, InfoRouter(handlers).route)
  }

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
    var user: Option[Response.User] = None
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      user = Some(responseAs[Response.User])
    }
    user.isDefined shouldBe true

    val rl = Request.Login(user.get.email, password)
    var resp: Option[OAuthToken] = None
    Post("/users/login", rl) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      resp = Some(responseAs[OAuthToken])
    }
    resp should not be None
    val token = resp.get.access_token

    val ruu = Request.UpdateUser(user.get.id, "Flipje", password, born)
    Put("/users", ruu) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[Response.User].name shouldEqual "Flipje"
    }

    Get(s"/users/${user.get.id}") ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[Response.User].name shouldEqual "Flipje"
    }
  }

  "Create and Delete User" should "return the deleted User" in {
    val eeeemail = "create.delete@test.er"
    val rcu = Request.CreateUser("CreateAndDelete", eeeemail, password, born)

    var userCount = 0
    var sessionCount = 0

    Get("/info") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val info = responseAs[Response.Info]
      userCount = info.users
      sessionCount = info.sessions
    }

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

    Get("/info") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val info = responseAs[Response.Info]
      info.users should equal(userCount + 1)
      info.sessions should equal(sessionCount + 1)
    }

    val rdu = Request.DeleteUser(eeeemail)
    Delete("/users", rdu) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Accepted
      responseAs[Response.User].email shouldEqual rcu.email
    }

    Get("/info") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val info = responseAs[Response.Info]
      info.users should equal (userCount)
      info.sessions should equal (sessionCount)   // deleted user should not have session anymore
    }
  }

  "Create user, login and logout" should "reset session count" in {
    val eeeemail = "create.delete@test.er"
    val rcu = Request.CreateUser("CreateAndDelete", eeeemail, password, born)

    var userCount = 0
    var sessionCount = 0

    Get("/info") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val info = responseAs[Response.Info]
      userCount = info.users
      sessionCount = info.sessions
    }

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

    Get("/info") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val info = responseAs[Response.Info]
      info.users should equal(userCount + 1)
      info.sessions should equal(sessionCount + 1)
    }

    Put("/users/logout") ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
    }

    Get("/info") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[Response.Info].sessions should equal(sessionCount)
    }
  }

  override def beforeEach(): Unit = Repository.reset()
  override def afterAll(): Unit = testKit.shutdownTestKit()
}
