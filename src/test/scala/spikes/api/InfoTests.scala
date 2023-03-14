package spikes.api

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.handleRejections
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import spikes.SpikesTest
import spikes.behavior.Handlers
import spikes.model.Command
import spikes.route.InfoRouter
import spikes.route.InfoRouter.Info
import spikes.validate.Validation

import java.util.UUID

class InfoTests extends SpikesTest with ScalaFutures with ScalatestRouteTest with BeforeAndAfterAll {

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

  val handlers: ActorRef[Command] = testKit.spawn(Handlers(), "api-test-handlers")
  val route: Route = handleRejections(Validation.rejectionHandler) { InfoRouter(handlers).route }

  "GET info endpoint" should "be ok" in {
    Get("/info") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val info = responseAs[Info]
      info.users should be (0)
      info.sessions should be (0)
    }
  }

  "GET readiness endpoint" should "be ok" in {
    Get("/readiness") ~> route ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

  "GET liveness endpoint" should "be ok" in {
    Get("/liveness") ~> route ~> check {
      status shouldEqual StatusCodes.OK
    }
  }
}
