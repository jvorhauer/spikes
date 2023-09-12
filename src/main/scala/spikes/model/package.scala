package spikes

import io.hypersistence.tsid.TSID
import org.owasp.encoder.Encode
import scalikejdbc.ParameterBinderFactory
import spikes.validate.Validation.ErrorInfo

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneId}
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

  def next = TSID.Factory.getTsid256

  implicit class RichTSID(private val self: TSID) extends AnyVal {
    def created: LocalDateTime = LocalDateTime.ofInstant(self.getInstant, ZoneId.of("UTC"))
    def hashed: String = hash(self.toString)
  }

  implicit val tsidPBF: ParameterBinderFactory[TSID] = ParameterBinderFactory[TSID] {
    value => (stmt, idx) => stmt.setLong(idx, value.toLong)
  }
}
