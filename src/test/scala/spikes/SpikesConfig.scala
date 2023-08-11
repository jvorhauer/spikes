package spikes

import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.{Config, ConfigFactory}

object SpikesConfig {

  val config: Config = ConfigFactory.parseString(
      """
        |akka {
        |  actor {
        |    serializers {
        |      kryo = "io.altoo.akka.serialization.kryo.KryoSerializer"
        |    }
        |    serialization-bindings {
        |        "spikes.model.package$SpikeSerializable" = kryo
        |    }
        |  }
        |}""".stripMargin)
    .withFallback(EventSourcedBehaviorTestKit.config)

}
