package spikes

import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.db.Repository

trait SpikesTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {
  override def beforeEach(): Unit = Repository.reset()

  def waitForUser(): Unit = {
    var counter = 0
    while (counter < 10 && Repository.userCount() == 0) {
      counter += 1
      Thread.sleep(5)
    }
  }
}
