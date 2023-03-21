package spikes.api

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import io.circe.{ Decoder, Encoder }
import org.scalatest.concurrent.ScalaFutures
import spikes.SpikesTest
import spikes.behavior.{ Handlers, TestUser }
import spikes.model.*
import spikes.model.User.Response
import spikes.route.InfoRouter.Info
import spikes.route.{ InfoRouter, RequestError, UserRouter }
import spikes.validate.Validation
import wvlet.airframe.ulid.ULID

import java.time.LocalDate
import scala.util.Try

class UserRouterTests extends SpikesTest with ScalaFutures with ScalatestRouteTest with TestUser {

  implicit val ulidEncoder: Encoder[ULID] = Encoder.encodeString.contramap[ULID](_.toString())
  implicit val ulidDecoder: Decoder[ULID] = Decoder.decodeString.emapTry(str => Try(ULID.fromString(str)))
  implicit val statEncoder: Encoder[Status.Value] = Encoder.encodeEnumeration(Status) // for Task
  implicit val statDecoder: Decoder[Status.Value] = Decoder.decodeEnumeration(Status) // for Task

  implicit val ts: ActorSystem[Nothing] = system.toTyped

  val testKit: ActorTestKit = ActorTestKit(cfg)
  val handlers: ActorRef[Command] = testKit.spawn(Handlers(), "api-test-handlers")
  val route: Route = handleRejections(Validation.rejectionHandler) {
    concat(UserRouter(handlers).route, InfoRouter(handlers).route)
  }

  "Post without User request" should "return bad request" in {
    Post("/users") ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.BadRequest
      header("Location") should be(None)
    }
  }

  "Post with invalid user request" should "return bad request with reasons" in {
    val rcu = User.Post("", "", "", LocalDate.now())
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.BadRequest
      header("Location") should be(None)
    }
  }

  "Post a valid User request" should "return a created user" in {
    val rcu = User.Post(name, email, password, born)
    var location: String = "-"
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      header("Location") should not be None
      location = header("Location").map(_.value()).getOrElse("none")
      responseAs[Response].name shouldEqual name
    }
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Conflict
      responseAs[RequestError] shouldEqual RequestError(s"email $email already in use")
    }
    Get(location) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[User.Response].name shouldEqual name
    }
  }

  "Create and Update User" should "return updated User" in {
    val rcu = User.Post("Created", "user-router@miruvor.nl", password, born)
    var user: Option[User.Response] = None
    var location: String = "-"
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      user = Some(responseAs[User.Response])
      location = header("Location").map(_.value()).getOrElse("none")
    }
    user.isDefined shouldBe true
    location shouldEqual s"/users/${user.get.id}"

    Get(s"/users/${user.get.id}") ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[User.Response].name shouldEqual "Created"
    }

    val rl = User.Authenticate(user.get.email, password)
    var resp: Option[OAuthToken] = None
    Post("/users/login", rl) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      resp = Some(responseAs[OAuthToken])
    }
    resp should not be empty
    val token = resp.get.access_token

    val ruu = User.Put(user.get.id, "Updated", password, born)
    Put("/users", ruu) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[User.Response].name shouldEqual "Updated"
    }

    Thread.sleep(100) // wait for update to finish

    Get(s"/users/${user.get.id}") ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[User.Response].name shouldEqual "Updated"
    }
  }

  "Create and Delete User" should "return the deleted User" in {
    val eeeemail = "create.delete@test.er"
    val rcu = User.Post("CreateAndDelete", eeeemail, password, born)

    var userCount: Long = 0
    var sessionCount = 0

    Get("/info") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val info = responseAs[Info]
      userCount = info.users
      sessionCount = info.sessions
    }

    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      responseAs[User.Response].name shouldEqual rcu.name
    }

    val rl = User.Authenticate(eeeemail, password)
    var resp: Option[OAuthToken] = None
    Post("/users/login", rl) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      resp = Some(responseAs[OAuthToken])
    }
    resp should not be None
    val token = resp.get.access_token

    Get("/info") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val info = responseAs[Info]
      info.users should equal(userCount + 1)
      info.sessions should equal(sessionCount + 1)
    }

    Get("/users/me") ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
      val ur = responseAs[User.Response]
      ur.email should be(rcu.email)
    }

    val rdu = User.Delete(eeeemail)
    Delete("/users", rdu) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Accepted
      responseAs[User.Response].email shouldEqual rcu.email
    }

    Get("/info") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val info = responseAs[Info]
      info.users should equal(userCount)
      info.sessions should equal(sessionCount) // deleted user should not have session anymore
    }
  }

  "Create user, login and logout" should "reset session count" in {
    val eeeemail = "login.logout@test.er"
    val rcu = User.Post("CreateAndDelete", eeeemail, password, born)

    var userCount: Long = 0
    var sessionCount = 0

    Get("/info") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val info = responseAs[Info]
      userCount = info.users
      sessionCount = info.sessions
    }

    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      responseAs[User.Response].name shouldEqual rcu.name
    }

    val rl = User.Authenticate(eeeemail, password)
    var resp: Option[OAuthToken] = None
    Post("/users/login", rl) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      resp = Some(responseAs[OAuthToken])
    }
    resp should not be None
    val token = resp.get.access_token

    Get("/info") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val info = responseAs[Info]
      info.users should equal(userCount + 1)
      info.sessions should equal(sessionCount + 1)
    }

    Put("/users/logout") ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
    }

    Get("/info") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[Info].sessions should equal(sessionCount)
    }
  }

  "Login with unkown credentials" should "be rejected" in {
    val ua = User.Authenticate("unknown@nonexiste.nt", "Welkom123!")
    Post("/users/login", ua) ~> route ~> check {
      status should be (StatusCodes.BadRequest)
    }

    Post("/users/login", """{"emai":"misformed@no.no", "password", "Welokm123!"}""") ~> Route.seal(route) ~> check {
      status should be (StatusCodes.BadRequest)
    }
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()
}
