package spikes

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

trait SpikesTestBase extends AnyFlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {
  val cfg: Config = ConfigFactory.parseString(
    """
      |akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      |akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      |scalikejdbc.global.loggingSQLErrors = true
      |kamon.enabled = no
      |spikes.persistence.version = 1
      |spikes.token.expires = 5 minutes
      |""".stripMargin)
}
