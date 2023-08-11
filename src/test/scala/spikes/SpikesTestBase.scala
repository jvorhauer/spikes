package spikes

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

trait SpikesTestBase extends AnyFlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {
  val cfg: Config = ConfigFactory.parseString("""akka.persistence.journal.plugin = "akka.persistence.journal.inmem"""")
}
