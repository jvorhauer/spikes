package spikes.behavior

import akka.actor.testkit.typed.scaladsl.{ManualTime, ScalaTestWithActorTestKit, TestProbe}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import spikes.model.Command

class SessionReaperTests extends ScalaTestWithActorTestKit(ManualTime.config) with AnyFlatSpecLike with Matchers {

  val probe: TestProbe[Command] = TestProbe[Command]()
  val manualTime: ManualTime = ManualTime()

  "Session Reaper" should "reap" in {
    spawn(SessionReaper(probe.ref, 10.millis))
    manualTime.expectNoMessageFor(9.millis)
    manualTime.timePasses(2.millis)
    probe.expectMessageType[SessionReaper.Reap]
  }
}
