package spikes.model

import io.hypersistence.tsid.TSID
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class TsidFactoryTests extends AnyWordSpecLike with Matchers {

  val max = 100000

  "The TSID.Factory" should {
    "produce unique IDs" in {
      Set.from((1 to max).map(_ => next)) should have size max
    }

    "toString and from" in {
      val id = next
      val str = id.toString
      str.length should be (13)
      TSID.from(str) should be (id)
    }

    "toLong and from" in {
      val id = next
      val long = id.toLong
      TSID.from(long) should be (id)
    }

    "instant is today" in {
      val id = next
      val in = id.getInstant
      val cet = in.atZone(zone)

      val nu = now

      cet.getYear should be (nu.getYear)
      cet.getMonthValue should be (nu.getMonthValue)
      cet.getDayOfMonth should be (nu.getDayOfMonth)
      cet.getHour should be (nu.getHour)
      cet.getMinute should be (nu.getMinute)
    }
  }
}
