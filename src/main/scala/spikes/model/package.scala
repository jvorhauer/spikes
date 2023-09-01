package spikes

import org.owasp.encoder.Encode
import scalikejdbc.ParameterBinderFactory
import spikes.validate.Validation.ErrorInfo
import wvlet.airframe.ulid.ULID

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.util.Locale

package object model {

  trait SpikeSerializable

  trait Request {
    def validated: Set[ErrorInfo] = Set.empty
  }

  trait Command extends SpikeSerializable

  trait Entity extends SpikeSerializable {
    def id: ULID
  }

  trait Event extends SpikeSerializable {
    def id: ULID
  }

  trait ResponseT extends SpikeSerializable {
    def id: ULID
  }

  trait StateT


  private val md = MessageDigest.getInstance("SHA-256")
  private def toHex(ba: Array[Byte]): String = ba.map(s => String.format(Locale.US, "%02x", s)).mkString("")
  def hash(s: String): String = toHex(md.digest(s.getBytes(StandardCharsets.UTF_8)))
  def hash(ulid: ULID): String = hash(ulid.toString)
  def encode(s: String): String = Encode.forHtmlContent(s)
  def now: LocalDateTime = LocalDateTime.now()
  def today: LocalDate = LocalDate.now()
  def next = ULID.newULID
  val DTF: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

  implicit class RichULID(private val self: ULID) extends AnyVal {
    def created: LocalDateTime = LocalDateTime.ofInstant(self.toInstant, ZoneId.of("UTC"))
    def hashed: String = hash(self)
  }

  implicit val ulidPBF: ParameterBinderFactory[ULID] = ParameterBinderFactory[ULID] {
    value => (stmt, idx) => stmt.setString(idx, value.toString())
  }
}
