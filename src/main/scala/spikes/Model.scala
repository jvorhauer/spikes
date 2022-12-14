package spikes

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.{Locale, UUID}

trait CborSerializable

trait Request
trait Command  extends CborSerializable
trait Event    extends CborSerializable
trait Entity   extends CborSerializable { def id: UUID }
trait Response extends CborSerializable

object Hasher {
  private val md = MessageDigest.getInstance("SHA-256")
  private def toHex(ba: Array[Byte]): String = ba.map(s => String.format(Locale.US, "%02x", s)).mkString("")
  def hash(s: String): String = toHex(md.digest(s.getBytes(StandardCharsets.UTF_8)))
}
