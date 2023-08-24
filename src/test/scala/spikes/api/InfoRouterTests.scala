package spikes.api

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.handleRejections
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import scalikejdbc.DBSession
import spikes.behavior.Manager.Info
import spikes.{Spikes, SpikesTestBase}
import spikes.behavior.{Manager, TestUser}
import spikes.model.Command
import spikes.route.InfoRouter
import spikes.validate.Validation

class InfoRouterTests extends SpikesTestBase with ScalaFutures with ScalatestRouteTest with TestUser with BeforeAndAfterEach {

  implicit val session: DBSession = Spikes.init

  val testKit: ActorTestKit = ActorTestKit(cfg)
  implicit val ts: ActorSystem[Nothing] = testKit.internalSystem
  val manager: ActorRef[Command] = testKit.spawn(Manager(), "manager-test-actor")

  val route: Route = handleRejections(Validation.rejectionHandler) {
      InfoRouter(manager).route
  }

  "Get Info" should "respond with system info" in {
    Get("/info") ~> route ~> check {
      status should be (StatusCodes.OK)
      val res = responseAs[Info]
      res.users should be (0)
      res.notes should be (0)
      res.sessions should be (0)
      res.recovered should be (true)
    }
  }

  "Readiness" should "be ok" in {
    Get("/readiness") ~> route ~> check {
      status should be (StatusCodes.OK)
    }
  }

  "Liveness" should "be ok" in {
    Get("/liveness") ~> route ~> check {
      status should be(StatusCodes.OK)
    }
  }

  "Check" should "be ok" in {
    Get("/check") ~> route ~> check {
      status should be(StatusCodes.OK)
    }
  }
}
