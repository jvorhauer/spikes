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
import spikes.model.{Command, Session, Tag, User, next, now, today}
import spikes.route.{TagRouter, UserRouter}
import spikes.validate.Validation
import spikes.{Spikes, SpikesTestBase}

import scala.util.Try

class TagRouterTests extends SpikesTestBase with ScalaFutures with ScalatestRouteTest with TestUser with BeforeAndAfterEach {

  implicit val idEncoder: Encoder[TSID] = Encoder.encodeString.contramap[TSID](_.toString)
  implicit val idDecoder: Decoder[TSID] = Decoder.decodeString.emapTry(str => Try(TSID.from(str)))

  implicit val session: DBSession = Spikes.init

  val testKit: ActorTestKit = ActorTestKit(cfg)
  implicit val ts: ActorSystem[Nothing] = testKit.internalSystem
  val manager: ActorRef[Command] = testKit.spawn(Manager(), "manager-test-actor")

  val route: Route = handleRejections(Validation.rejectionHandler) {
    concat(TagRouter(manager).route, UserRouter(manager).route)
  }

  val path = "/tags"

  "Post without authorisation token" should "return 401 Unauthorized" in {
    Tag.Post("")
    Post(path) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Unauthorized
    }
  }

  "Post without title" should "return bad request" in {
    val usr = User.save(User.Created(next, "Tester", "test@er.nl", "Welkom123!", today.minusYears(32), None))
    val ses = User.find(usr.id).map(Session.save(_, now.plusHours(1)))
    ses.isDefined should be (true)
    val token = ses.get.asOAuthToken.access_token
    val rtc = Tag.Post("")
    Post(path, rtc) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.BadRequest
    }
  }


  "Post with title" should "return the new Tag" in {
    val usr = User.save(User.Created(next, "Tester", "test@er.nl", "Welkom123!", today.minusYears(32), None))
    val ses = User.find(usr.id).map(Session.save(_, now.plusHours(1)))
    ses.isDefined should be(true)
    val token = ses.get.asOAuthToken.access_token
    val rtc = Tag.Post("tag title")
    Post(path, rtc) ~> Authorization(OAuth2BearerToken(token)) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      val res = responseAs[Tag.Response]
      res.title should be ("tag title")
    }
  }

  override def beforeEach(): Unit = gc()
  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    gc()
  }

  private def gc(): Unit = {
    Session.list.foreach(s => s.remove())
    User.list(limit = Int.MaxValue).foreach(u => u.remove())
    Tag.list.foreach(tag => tag.remove())
  }
}
