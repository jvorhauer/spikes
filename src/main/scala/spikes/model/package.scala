package spikes

import io.hypersistence.tsid.TSID
import org.owasp.encoder.Encode
import scalikejdbc.ParameterBinderFactory
import spikes.validate.Validated
import spikes.validate.Validated.Passed
import spikes.validate.Validator.ValidationError

import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneId, ZonedDateTime}
import java.util.Locale
import scala.util.matching.Regex

package object model {

  type SPID = TSID

  trait Request { self =>
    def validated: Validated[ValidationError, Request] = Passed(self)
  }

  trait Command extends Serializable

  trait Event extends Serializable {
    def id: SPID
  }

  trait ResponseT extends Serializable {
    def id: SPID
  }

  trait Entity extends Serializable {
    def id: SPID
  }


  private val md = MessageDigest.getInstance("SHA-256")
  private def toHex(ba: Array[Byte]): String = ba.map(s => String.format(Locale.US, "%02x", s)).mkString("")
  def hash(s: String): String = toHex(md.digest(s.getBytes(StandardCharsets.UTF_8)))
  def hash(tsid: TSID): String = hash(tsid.toString)

  val clean: String => String = s => Option(s).map(_.trim).map(Encode.forHtml).getOrElse("")

  val zone: ZoneId = ZoneId.of("CET")
  def now: LocalDateTime = ZonedDateTime.now(zone).toLocalDateTime
  def today: LocalDate = LocalDate.now(zone)
  val DTF: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

  private val idFactory: TSID.Factory = TSID.Factory.builder()
    .withRandom(SecureRandom.getInstance("SHA1PRNG", "SUN"))
    .withNodeBits(8)
    .withNode(InetAddress.getLocalHost.getAddress()(3).toInt & 0xFF).build()
  def next: TSID = idFactory.generate()


  implicit class RichTSID(private val self: TSID) extends AnyVal {
    def created: LocalDateTime = LocalDateTime.ofInstant(self.getInstant, zone)
    def hashed: String = hash(self.toString)
  }

  implicit class ValidatableString(private val self: String) extends AnyVal {
    def matches(re: Regex): Boolean = re.matches(self)
  }

  implicit val tsidPBF: ParameterBinderFactory[TSID] = ParameterBinderFactory[TSID] {
    value => (stmt, idx) => stmt.setLong(idx, value.toLong)
  }
}
