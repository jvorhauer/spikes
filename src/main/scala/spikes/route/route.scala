package spikes

import akka.util.Timeout
import io.circe.{Decoder, Encoder}
import io.hypersistence.tsid.TSID
import spikes.model.Status
import spikes.model.Access

import scala.concurrent.duration.DurationInt
import scala.util.Try

package object route {

  implicit val timeout: Timeout = 3.seconds

  implicit val tsidEncoder: Encoder[TSID] = Encoder.encodeString.contramap[TSID](_.toString)
  implicit val tsidDecoder: Decoder[TSID] = Decoder.decodeString.emapTry { str => Try(TSID.from(str)) }
  implicit val statusEncoder: Encoder[Status.Value] = Encoder.encodeEnumeration(Status)
  implicit val statusDecoder: Decoder[Status.Value] = Decoder.decodeEnumeration(Status)
  implicit val accEncoder: Encoder[Access.Value] = Encoder.encodeEnumeration(Access)
  implicit val accDecoder: Decoder[Access.Value] = Decoder.decodeEnumeration(Access)
}
