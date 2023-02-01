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
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.InfoRouter
import spikes.behavior.{Finder, Handlers, Query, Reader}
import spikes.model._
import spikes.validate.Validation
import wvlet.airframe.ulid.ULID

import java.util.UUID
import scala.util.Try
import akka.actor.typed.ActorSystem

class EntriesTests extends AnyFlatSpec with Matchers with ScalaFutures with ScalatestRouteTest with BeforeAndAfterAll with TestUser {

  implicit val ulidEncoder: Encoder[ULID] = Encoder.encodeString.contramap[ULID](_.toString())
  implicit val ulidDecoder: Decoder[ULID] = Decoder.decodeString.emapTry { str => Try(ULID.fromString(str)) }
  implicit val statusEncoder: Encoder[Status.Value] = Encoder.encodeEnumeration(Status)
  implicit val statusDecoder: Decoder[Status.Value] = Decoder.decodeEnumeration(Status)

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
    concat(UserRouter(handlers, querier).route, InfoRouter(handlers).route, EntryRouter(handlers).route)
  }

  "Post a User and an Entry" should "return a created user and entry" in {
    val rcu = Request.CreateUser(name, email, password, born)
    var resp: Option[Response.User] = None
    Post("/users", rcu) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
      resp = Some(responseAs[Response.User])
      resp should not be None
      resp.get.name shouldEqual name
    }

    val rl = Request.Login(email, password)
    var token: Option[String] = None
    Post("/users/login", rl) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.OK
      token = Some(responseAs[OAuthToken].access_token)
    }
    token should not be None

    val rce = Request.CreateEntry("title", "body")
    Post("/entries", rce) ~> Authorization(OAuth2BearerToken(token.get)) ~> Route.seal(route) ~> check {
      status shouldEqual StatusCodes.Created
    }
  }
}