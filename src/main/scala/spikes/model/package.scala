package spikes

import io.hypersistence.tsid.TSID
import org.owasp.encoder.Encode
import scalikejdbc.ParameterBinderFactory
import spikes.validate.Validation.ErrorInfo

import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}
import java.util.Locale

package object model {

  type SPID = TSID

  trait SpikeSerializable

  trait Request {
    def validated: Set[ErrorInfo] = Set.empty
  }

  trait Command extends SpikeSerializable

  trait Entity extends SpikeSerializable {
    def id: SPID
  }

  trait Event extends SpikeSerializable {
    def id: SPID
  }

  trait ResponseT extends SpikeSerializable {
    def id: SPID
  }

  trait StateT


  private val md = MessageDigest.getInstance("SHA-256")
  private def toHex(ba: Array[Byte]): String = ba.map(s => String.format(Locale.US, "%02x", s)).mkString("")
  def hash(s: String): String = toHex(md.digest(s.getBytes(StandardCharsets.UTF_8)))
  def hash(tsid: TSID): String = hash(tsid.toString)
  def encode(s: String): String = Encode.forHtmlContent(s)
  def now: LocalDateTime = LocalDateTime.now()
  def today: LocalDate = LocalDate.now()
  val DTF: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

  private val idFactory: TSID.Factory = TSID.Factory.builder()
    .withRandom(SecureRandom.getInstance("SHA1PRNG", "SUN"))
    .withCustomEpoch(Instant.parse("2023-04-01T00:00:00.000Z"))
    .withNodeBits(8)
    .withNode(InetAddress.getLocalHost.getAddress()(3).toInt & 0xFF).build()
  def next: TSID = idFactory.generate()

  implicit class RichTSID(private val self: TSID) extends AnyVal {
    def created: LocalDateTime = LocalDateTime.ofInstant(self.getInstant, ZoneId.of("UTC"))
    def hashed: String = hash(self.toString)
  }

  implicit val tsidPBF: ParameterBinderFactory[TSID] = ParameterBinderFactory[TSID] {
    value => (stmt, idx) => stmt.setLong(idx, value.toLong)
  }
}
