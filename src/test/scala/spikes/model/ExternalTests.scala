package spikes.model

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.pattern.StatusReply
import scala.concurrent.ExecutionContextExecutor
import wvlet.airframe.ulid.ULID

class ExternalTests extends AnyFlatSpec with ScalatestRouteTest with Matchers {

  private val testkit = ActorTestKit()
  private val probe = testkit.createTestProbe[StatusReply[External.Response]]().ref

  implicit val ec: ExecutionContextExecutor = testkit.system.executionContext

  private val xml = "<test>test</test>"


  "An External Request" should "become an External Command" in {
    val er = External.Post(xml)
    val ec = er.asCmd(probe)
    ec.body shouldEqual xml
  }

  "An External Create command" should "become an External Created event" in {
    val id = ULID.newULID
    val ec = External.Create(id, xml, probe)
    val ee = ec.asEvent
    ee.id should be (id)
    ee.id should be (ec.id)
    ee.body should be (ec.body)
  }

  "An External Created event" should "become an External entity" in {
    val id = ULID.newULID
    val ec = External.Created(id, xml)
    val ee = ec.asEntity
    ee.id should be (id)
    ee.id should be (ec.id)
    ee.body should be (xml)
    ee.body should be (ec.body)
  }

  "An External entity" should "become an External Response" in {
    val id = ULID.newULID
    val ee = External(id, xml)
    val er = ee.asResponse
    er.id should be (id)
    er.id should be (ee.id)
    er.body should be (xml)
    er.body should be (ee.body)
  }
}
