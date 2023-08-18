package spikes

import akka.util.Timeout
import io.circe.{Decoder, Encoder}
import spikes.model.{Access, Status}
import wvlet.airframe.ulid.ULID

import scala.concurrent.duration.DurationInt
import scala.util.Try

package object tapir {

  implicit val timeout: Timeout = 1.seconds

  implicit val ulidEncoder: Encoder[ULID] = Encoder.encodeString.contramap[ULID](_.toString())
  implicit val ulidDecoder: Decoder[ULID] = Decoder.decodeString.emapTry { str => Try(ULID.fromString(str)) }
  implicit val statusEncoder: Encoder[Status.Value] = Encoder.encodeEnumeration(Status)
  implicit val statusDecoder: Decoder[Status.Value] = Decoder.decodeEnumeration(Status)
  implicit val accEncoder: Encoder[Access.Value] = Encoder.encodeEnumeration(Access)
  implicit val accDecoder: Decoder[Access.Value] = Decoder.decodeEnumeration(Access)
}
