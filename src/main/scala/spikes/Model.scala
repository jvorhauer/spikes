package spikes

import spikes.model.User

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.{Locale, UUID}

trait CborSerializable

trait Request {
  val asCmd: Command
}
trait Command extends CborSerializable {
  val asEvent: Event
}
trait Event extends CborSerializable
trait Entity { val id: UUID }
trait Response

object Hasher {
  private val md = MessageDigest.getInstance("SHA-256")
  private def toHex(ba: Array[Byte]): String = ba.map(s => String.format(Locale.US, "%02x", s)).mkString("")
  def hash(s: String): String = toHex(md.digest(s.getBytes(StandardCharsets.UTF_8)))
}


case class Note(id: UUID, owner: User, title: String, body: String) extends Entity
case class Bookmark(id: UUID, marker: User, location: String, title: String, description: String) extends Entity
case class Task(id: UUID, reporter: User, title: String, description: String, due: LocalDateTime) extends Entity
case class Blog(id: UUID, owner: User, title: String, body: String, embargo: LocalDateTime) extends Entity
