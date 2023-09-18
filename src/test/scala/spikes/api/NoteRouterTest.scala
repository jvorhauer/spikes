package spikes.api

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import io.circe.{Decoder, Encoder}
import io.hypersistence.tsid.TSID
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import scalikejdbc.DBSession
import spikes.behavior.{Manager, TestUser}
import spikes.model.{Access, Command, Note, OAuthToken, Status, User, next, now}
import spikes.route.{NoteRouter, UserRouter}
import spikes.validate.Validation
import spikes.{Spikes, SpikesTestBase, model}

import scala.util.Try

class NoteRouterTest extends SpikesTestBase with ScalaFutures with ScalatestRouteTest with TestUser with BeforeAndAfterEach {

  implicit val idEncoder: Encoder[TSID] = Encoder.encodeString.contramap[TSID](_.toString)
  implicit val idDecoder: Decoder[TSID] = Decoder.decodeString.emapTry(str => Try(TSID.from(str)))
  implicit val statEncoder: Encoder[Status.Value] = Encoder.encodeEnumeration(Status)
  implicit val statDecoder: Decoder[Status.Value] = Decoder.decodeEnumeration(Status)
  implicit val accEncoder : Encoder[Access.Value] = Encoder.encodeEnumeration(Access)
  implicit val accDecoder : Decoder[Access.Value] = Decoder.decodeEnumeration(Access)

  implicit val session: DBSession = Spikes.init

  val testKit: ActorTestKit = ActorTestKit(cfg)
  implicit val ts: ActorSystem[Nothing] = testKit.internalSystem
  val manager: ActorRef[Command] = testKit.spawn(Manager(), "manager-test-actor")

  val route: Route = handleRejections(Validation.rejectionHandler) {
    concat(
      UserRouter(manager).route, NoteRouter().route
    )
  }

  "Post a new Note" should "get a list of one for the current user" in {
    val rcu = User.Post(name, "getme@now.nl", password, born, Some("bio graphy"))
    var user: Option[User.Response] = None
    var location: String = "-"
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      header("Location") should not be None
      location = header("Location").map(_.value()).getOrElse("none")
      user = Some(responseAs[User.Response])
    }
    user.isDefined shouldBe true
    location shouldEqual s"/users/${user.get.id}"

    Get(location) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[User.Response].name shouldEqual name
    }

    val rl = User.Authenticate(rcu.email, password)
    var resp: Option[OAuthToken] = None
    Post("/users/login", rl) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      resp = Some(responseAs[OAuthToken])
    }
    resp should not be empty
    val token = resp.get.access_token

    Get("/users/me") ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
      val ur = responseAs[User.Response]
      ur.name should be(name)
      ur.email should be("getme@now.nl")
      ur.bio should not be empty
      ur.bio.get should be("bio graphy")
    }

    val rnp = Note.Post("title", "body", now.plusDays(7))
    var noteId: TSID = next
    Post("/notes", rnp) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be (StatusCodes.Created)
      header("Location") should not be empty
      location = header("Location").map(_.value()).getOrElse("none")
      noteId = responseAs[Note.Response].id
    }

    Get(location) ~> Route.seal(route) ~> check {
      status should be (StatusCodes.OK)
      responseAs[Note.Response].title should be ("title")
    }

    Get("/notes/mine") ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be (StatusCodes.OK)
      val r = responseAs[List[model.Note.Response]]
      r.size should be (1)
    }

    var note: Option[model.Note.Response] = None
    Get(s"/notes/$noteId") ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
      note = Some(responseAs[Note.Response])
      note should not be empty
      note.get.title should be("title")
    }
    note should not be empty

    val put = Note.Put(noteId, user.get.id, "updated", "ubody", note.get.due, note.get.status, note.get.access)
    Put("/notes", put) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be (StatusCodes.OK)
      val res = responseAs[Note.Response]
      res.title should be ("updated")
    }

    Delete(s"/notes/$noteId") ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be (StatusCodes.OK)
    }

    Delete(s"/notes/${next}") ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be (StatusCodes.BadRequest)
    }

    Delete(s"/notes/${next}") ~> Route.seal(route) ~> check {
      status should be (StatusCodes.Unauthorized)
    }

    Get("/notes/mine") ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be(StatusCodes.OK)
      val r = responseAs[List[model.Note.Response]]
      r.size should be(0)
    }

    Get(s"/notes/${next}") ~> Route.seal(route) ~> check {
      status should be (StatusCodes.NotFound)
    }
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()

  override def beforeEach(): Unit = {
    User.removeAll()
  }
}
