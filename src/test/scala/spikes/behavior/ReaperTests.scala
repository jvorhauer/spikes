package spikes.behavior

import akka.actor.testkit.typed.scaladsl.{ManualTime, ScalaTestWithActorTestKit, TestProbe}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import spikes.model.Command
import spikes.model.Command.Reap

import scala.concurrent.duration._


class ReaperTests extends ScalaTestWithActorTestKit(ManualTime.config) with AnyFlatSpecLike with Matchers {

  val probe = TestProbe[Command]()
  val manualTime = ManualTime()

  "Reaper" should "reap" in {
    spawn(Reaper(probe.ref, 10.millis))
    manualTime.expectNoMessageFor(9.millis)
    manualTime.timePasses(2.millis)
    probe.expectMessageType[Reap]
  }
}
