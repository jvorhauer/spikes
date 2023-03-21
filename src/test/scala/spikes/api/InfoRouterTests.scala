package spikes.api

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.handleRejections
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import org.scalatest.concurrent.ScalaFutures
import spikes.SpikesTest
import spikes.behavior.Handlers
import spikes.build.BuildInfo
import spikes.model.Command
import spikes.route.InfoRouter
import spikes.route.InfoRouter.Info
import spikes.validate.Validation

class InfoRouterTests extends SpikesTest with ScalaFutures with ScalatestRouteTest {

  implicit val ts: ActorSystem[Nothing] = system.toTyped

  val testKit: ActorTestKit = ActorTestKit(cfg)
  val handlers: ActorRef[Command] = testKit.spawn(Handlers(), "api-test-handlers")
  val route: Route = handleRejections(Validation.rejectionHandler)(InfoRouter(handlers).route)

  "GET info endpoint" should "be ok" in {
    Get("/info") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val info = responseAs[Info]
      info.users should be >= 0L
      info.sessions should be >= 0
      info.tasks should be >= 0L
      info.bookmarks should be >= 0L
      info.recovered should be(true)
      info.build should startWith(BuildInfo.version)
      println(s"build info: ${info.build}")
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

  override def afterAll(): Unit = testKit.shutdownTestKit()
}
