package spikes.tapir

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import io.circe.{Decoder, Encoder}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import scalikejdbc.DBSession
import spikes.{Spikes, SpikesTestBase}
import spikes.behavior.{Manager, TestUser}
import spikes.model.{Command, OAuthToken, User, today}
import wvlet.airframe.ulid.ULID

import scala.util.Try
import akka.http.scaladsl.server.Route

class UserEndpointsSpec extends SpikesTestBase with ScalaFutures with ScalatestRouteTest with TestUser with BeforeAndAfterEach {

  implicit val ulidEncoder: Encoder[ULID] = Encoder.encodeString.contramap[ULID](_.toString())
  implicit val ulidDecoder: Decoder[ULID] = Decoder.decodeString.emapTry(str => Try(ULID.fromString(str)))

  implicit val session: DBSession = Spikes.init

  val testKit: ActorTestKit = ActorTestKit(cfg)
  implicit val ts: ActorSystem[Nothing] = testKit.internalSystem
  val manager: ActorRef[Command] = testKit.spawn(Manager(), "manager-test-actor")
  val route: Route = UserEndpoints(manager).route

  override def beforeEach(): Unit = {
    super.beforeEach()
    User.Repository.nuke()
  }


  "Get List of Users" should "return a List of Users" in {

    var id: ULID = ULID.newULID

    val post = User.Post("tester", "test@er.nl", "Welkom123!", today.minusYears(15), Some("ga toch niet weg"))
    Post("/susers", post) ~> route ~> check {
      status should be (StatusCodes.OK)
      val res = responseAs[User.Response]
      res.name should be ("tester")
      id = res.id
    }

    Get("/susers?limit=10") ~> route ~> check {
      status should be (StatusCodes.OK)
      val list = responseAs[List[User.Response]]
      list should have size(1)
    }

    var token: String = ""
    val ua = User.Authenticate("test@er.nl", "Welkom123!")
    Post("/susers/login", ua) ~> route ~> check {
      status should be (StatusCodes.OK)
      token = responseAs[OAuthToken].access_token
    }
    token should not be empty

    val put = User.Put(id, "toaster", "NotWelkom234!", post.born, post.bio)
    Put("/susers", put) ~> Authorization(OAuth2BearerToken(token)) ~> route ~> check {
      status should be (StatusCodes.OK)
      val res = responseAs[User.Response]
      res.name should be ("toaster")
      res.email should be ("test@er.nl")
    }

    Get("/susers?limit=5") ~> route ~> check {
      status should be(StatusCodes.OK)
      val list = responseAs[List[User.Response]]
      list should have size (1)
    }

    Get(s"/susers/$id") ~> route ~> check {
      status should be (StatusCodes.OK)
      val res = responseAs[User.Response]
      res.name should be ("toaster")
    }
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()
}
