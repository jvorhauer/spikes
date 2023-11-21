package spikes.api

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.*
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
import spikes.model.*
import spikes.route.{RequestError, UserRouter}
import spikes.validate.Validator
import spikes.{Spikes, SpikesTestBase}

import scala.util.Try

class UserRouterTests extends SpikesTestBase with ScalaFutures with ScalatestRouteTest with TestUser with BeforeAndAfterEach {

  implicit val idEncoder: Encoder[TSID] = Encoder.encodeString.contramap[TSID](_.toString)
  implicit val idDecoder: Decoder[TSID] = Decoder.decodeString.emapTry(str => Try(TSID.from(str)))
  implicit val statEncoder: Encoder[Status.Value] = Encoder.encodeEnumeration(Status) // for Note
  implicit val statDecoder: Decoder[Status.Value] = Decoder.decodeEnumeration(Status) // for Note

  implicit val session: DBSession = Spikes.init

  val testKit: ActorTestKit = ActorTestKit(cfg)
  implicit val ts: ActorSystem[Nothing] = testKit.internalSystem
  val manager: ActorRef[Command] = testKit.spawn(Manager(), "manager-test-actor")
  val route: Route = handleRejections(Validator.rejectionHandler) {
    UserRouter(manager).route
  }
  val path = "/users"

  "Post without User request" should "return bad request" in {
    Post(path) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.BadRequest
      header("Location") should be(None)
    }
  }

  "Post with invalid user request" should "return bad request with reasons" in {
    val rcu = User.Post("", "", "", today)
    Post(path, rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.BadRequest
      header("Location") should be(None)
      val res = responseAs[List[Validator.ErrorInfo]]
      res should have size 4
      println(res)
    }
  }

  "Post a valid User request" should "return a created user" in {
    val rcu = User.Post(name, email, password, born)
    var location: String = "-"
    Post(path, rcu) ~> Route.seal(route) ~> check {
      handled should be(true)
      status shouldEqual StatusCodes.Created
      header("Location") should not be None
      location = header("Location").map(_.value()).getOrElse("none")
      contentType.mediaType should be (MediaTypes.`application/json`)
      responseAs[User.Response].name shouldEqual name
    }
    Post(path, rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Conflict
      responseAs[RequestError] shouldEqual RequestError(s"email $email already in use")
    }

    Get(location) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[User.Response].name shouldEqual name
    }
  }

  "Post a valid User request" should "allow login" in {
    val rcu = User.Post(name, "logmein@now.nl", password, born)
    var user: Option[User.Response] = None
    var location: String = "-"
    Post(path, rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      header("Location") should not be None
      location = header("Location").map(_.value()).getOrElse("none")
      contentType.mediaType should be(MediaTypes.`application/json`)
      user = Some(responseAs[User.Response])
    }
    user.isDefined shouldBe true
    location shouldEqual s"$path/${user.get.id}"

    Get(location) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[User.Response].name shouldEqual name
    }

    val rl = User.Authenticate(rcu.email, password)
    var resp: Option[OAuthToken] = None
    Post(s"$path/login", rl) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      resp = Some(responseAs[OAuthToken])
    }
    resp should not be empty
    val token = resp.get.access_token

    Get("/users/me")  ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be (StatusCodes.OK)
    }

    val ruu = User.Put(user.get.id, Some("Updated"), None, None)
    Put(path, ruu) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[User.Response].name shouldEqual "Updated"
    }
  }

  "Get my details" should "return state of logged in user" in {
    val rcu = User.Post(name, "getme@now.nl", password, born, Some("bio graphy"))
    var user: Option[User.Response] = None
    var location: String = "-"
    Post(path, rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      header("Location") should not be None
      location = header("Location").map(_.value()).getOrElse("none")
      contentType.mediaType should be(MediaTypes.`application/json`)
      user = Some(responseAs[User.Response])
    }
    user.isDefined shouldBe true
    location shouldEqual s"$path/${user.get.id}"

    Get(location) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[User.Response].name shouldEqual name
    }

    val rl = User.Authenticate(rcu.email, password)
    var resp: Option[OAuthToken] = None
    Post(s"$path/login", rl) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      resp = Some(responseAs[OAuthToken])
    }
    resp should not be empty
    val token = resp.get.access_token

    Get(s"$path/me") ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status should be (StatusCodes.OK)
      val ur = responseAs[User.Response]
      ur.name should be (name)
      ur.email should be ("getme@now.nl")
      ur.bio should not be empty
      ur.bio.get should be ("bio graphy")
    }
  }

  "Route Tags" should "create a new Tag" in {
    Tag.Post("test tag", "BBDB9B")  // color is Granny Smith Apple :-)
    Post("/tags")

  }


  override def afterAll(): Unit = {
    gc()
    testKit.shutdownTestKit()
  }
  override def beforeEach(): Unit = gc()

  private def gc(): Unit = {
    Session.list.foreach(_.remove())
    User.list(Int.MaxValue).foreach(_.remove())
  }
}
