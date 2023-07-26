package spikes

import spikes.validate.Validation.ErrorInfo
import wvlet.airframe.ulid.ULID

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.{LocalDateTime, ZoneId}
import java.util.Locale

package object model {

  trait Command

  trait Entity {
    def id: ULID
  }

  trait Event {
    def id: ULID
  }

  trait Request {
    def validated: Set[ErrorInfo] = Set.empty
  }

  trait Respons {
    def id: ULID
  }


  private val md = MessageDigest.getInstance("SHA-256")
  private def toHex(ba: Array[Byte]): String = ba.map(s => String.format(Locale.US, "%02x", s)).mkString("")
  def hash(s: String): String = toHex(md.digest(s.getBytes(StandardCharsets.UTF_8)))
  def hash(ulid: ULID): String = hash(ulid.toString)

  def now: LocalDateTime = LocalDateTime.now()

  def next = ULID.newULID

  implicit class RichULID(private val self: ULID) extends AnyVal {
    def created: LocalDateTime = LocalDateTime.ofInstant(self.toInstant, ZoneId.of("UTC"))
  }
}
