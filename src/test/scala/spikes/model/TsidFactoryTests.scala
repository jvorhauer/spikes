package spikes.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TsidFactoryTests extends AnyFlatSpec with Matchers {

  val max = 100000

  "The TSID.Factory" should "produce unique IDs" in {
    Set.from((1 to max).map(_ => next)) should have size (max)
  }
}
