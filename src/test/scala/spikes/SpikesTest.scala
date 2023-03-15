package spikes

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait SpikesTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll
