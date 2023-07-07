package spikes.api

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.server.Directives.handleRejections
import akka.http.scaladsl.server.{ Directives, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import io.circe.{ Decoder, Encoder }
import org.scalatest.concurrent.ScalaFutures
import spikes.SpikesTest
import spikes.behavior.{ Handlers, TestUser }
import spikes.model.{ next, Command, OAuthToken, Status, Note, User }
import spikes.route.{ InfoRouter, NoteRouter, UserRouter }
import spikes.validate.Validation
import wvlet.airframe.ulid.ULID

import java.time.LocalDateTime
import scala.util.Try

class NoteRouterTest extends SpikesTest with ScalaFutures with ScalatestRouteTest with TestUser {

  implicit val ulidEncoder: Encoder[ULID] = Encoder.encodeString.contramap[ULID](_.toString())
  implicit val ulidDecoder: Decoder[ULID] = Decoder.decodeString.emapTry(str => Try(ULID.fromString(str)))
  implicit val statEncoder: Encoder[Status.Value] = Encoder.encodeEnumeration(Status) // for Note
  implicit val statDecoder: Decoder[Status.Value] = Decoder.decodeEnumeration(Status) // for Note

  implicit val ts: ActorSystem[Nothing] = system.toTyped
  val testKit: ActorTestKit = ActorTestKit(cfg)
  val handlers: ActorRef[Command] = testKit.spawn(Handlers(), "api-test-handlers")
  val route: Route = handleRejections(Validation.rejectionHandler) {
    Directives.concat(UserRouter(handlers).route, InfoRouter(handlers).route, NoteRouter(handlers).route)
  }

  "Create and Update Note" should "return updated Note" in {
    val up = User.Post("Created", "task-router@miruvor.nl", password, born)
    var user: Option[User.Response] = None
    var location: String = "-"
    Post("/users", up) ~> Route.seal(route) ~> check {
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

    Get("/users") ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[List[User.Response]].size should be >= 1
    }

    val rl = User.Authenticate(user.get.email, password)
    var resp: Option[OAuthToken] = None
    Post("/users/login", rl) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      resp = Some(responseAs[OAuthToken])
    }
    resp should not be empty
    val token = resp.get.access_token

    val tp = Note.Post("Test Title", "Test Body", LocalDateTime.now().plusDays(1), Status.New)
    Post("/notes", tp) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.Created)
      responseAs[Note.Response].title should be("Test Title")
    }

    var id: ULID = next
    Get(location) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
      val resp = responseAs[User.Response]
      resp.tasks should have size 1
      id = resp.tasks.head.id
    }

    Get(s"/notes/$id") ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
      val resp = responseAs[Note.Response]
      resp.title should be("Test Title")
      resp.body should be("Test Body")
    }

    val tu = Note.Put(id, "Updated Title", "Updated Test Body", LocalDateTime.now().plusDays(3), Status.ToDo)
    Put("/notes", tu) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
      val resp = responseAs[Note.Response]
      resp.title should be("Updated Title")
    }

    Get(location) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
      val resp = responseAs[User.Response]
      resp.tasks should have size 1
      resp.tasks.head.title should be("Updated Title")
    }

    Delete("/notes", Note.Delete(id)) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
    }

    Get(location) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
      val resp = responseAs[User.Response]
      resp.tasks should have size 0
    }
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()
}
