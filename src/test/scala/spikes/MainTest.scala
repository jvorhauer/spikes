package spikes

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}

class MainTest extends SpikesTest {

  val testKit: ActorTestKit = ActorTestKit("spikes-main-testkit", cfg)
  implicit val system: ActorSystem[Nothing] = testKit.system
  implicit val executionContext: ExecutionContextExecutor = testKit.system.executionContext

  "The Spikes actor" should "run" in {
    testKit.spawn[Nothing](Spikes(9999))
    Await.result(Http().singleRequest(Get("http://localhost:9999/readiness")), 2.seconds).status.intValue() should be (200)
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }
}
