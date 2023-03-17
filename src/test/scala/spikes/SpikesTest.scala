package spikes

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.typesafe.config.Config

trait SpikesTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {
  val cfg: Config = ConfigFactory.parseString("""akka.persistence.journal.plugin = "akka.persistence.journal.inmem"""")
}
