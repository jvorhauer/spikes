package spikes.api

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Directives.handleRejections
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import io.circe.{Decoder, Encoder}
import org.scalatest.concurrent.ScalaFutures
import spikes.SpikesTest
import spikes.behavior.{Handlers, TestUser}
import spikes.model.{Bookmark, Command, OAuthToken, Status, User, next}
import spikes.route.{BookmarkRouter, InfoRouter, TaskRouter, UserRouter}
import spikes.validate.Validation
import wvlet.airframe.ulid.ULID

import scala.util.Try

class BookmarkRouterTests extends SpikesTest with ScalaFutures with ScalatestRouteTest with TestUser {

  implicit val ulidEncoder: Encoder[ULID] = Encoder.encodeString.contramap[ULID](_.toString())
  implicit val ulidDecoder: Decoder[ULID] = Decoder.decodeString.emapTry { str => Try(ULID.fromString(str)) }
  implicit val statEncoder: Encoder[Status.Value] = Encoder.encodeEnumeration(Status) // for Task
  implicit val statDecoder: Decoder[Status.Value] = Decoder.decodeEnumeration(Status) // for Task

  implicit val ts: ActorSystem[Nothing] = system.toTyped
  val testKit: ActorTestKit = ActorTestKit(cfg)
  val handlers: ActorRef[Command] = testKit.spawn(Handlers(), "api-test-handlers")
  val route: Route = handleRejections(Validation.rejectionHandler) {
    Directives.concat(UserRouter(handlers).route, InfoRouter(handlers).route, TaskRouter(handlers).route, BookmarkRouter(handlers).route)
  }

  "A Bookmark" should "be cruddable" in {
    val up = User.Post("Created for Bookmark", "bookmark-router@miruvor.nl", password, born)
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
      responseAs[User.Response].name shouldEqual "Created for Bookmark"
    }

    val rl = User.Authenticate(user.get.email, password)
    var resp: Option[OAuthToken] = None
    Post("/users/login", rl) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      resp = Some(responseAs[OAuthToken])
    }
    resp should not be empty
    val token = resp.get.access_token

    val tp = Bookmark.Post("http://localhost:8080/users", "Test Title", "Test Body")
    Post("/bookmarks", tp) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.Created)
      val res1 = responseAs[Bookmark.Response]
      res1.title should be ("Test Title")
      res1.url should be ("http://localhost:8080/users")
      res1.body should be ("Test Body")
    }

    var id: ULID = next
    Get(location) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
      val resp = responseAs[User.Response]
      resp.bookmarks should have size 1
      id = resp.bookmarks.head.id
    }

    val bmp = Bookmark.Put(id, "http://updated:9090/bookmarks", "Updated Title", "Updated Body")
    Put("/bookmarks", bmp) ~> Authorization (OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
      val res1 = responseAs[Bookmark.Response]
      res1.title should be("Updated Title")
      res1.url should be("http://updated:9090/bookmarks")
      res1.body should be("Updated Body")
    }

    Get(location) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
      val resp = responseAs[User.Response]
      resp.bookmarks should have size 1
      resp.bookmarks.head.title should be ("Updated Title")
    }

    Delete("/bookmarks", Bookmark.Delete(id)) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
    }

    Get(location) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
      val resp = responseAs[User.Response]
      resp.bookmarks should have size 0
    }
  }
}
