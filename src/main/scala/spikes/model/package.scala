package spikes

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Locale

package object model {

  private val md = MessageDigest.getInstance("SHA-256")
  private def toHex(ba: Array[Byte]): String = ba.map(s => String.format(Locale.US, "%02x", s)).mkString("")
  def hash(s: String): String = toHex(md.digest(s.getBytes(StandardCharsets.UTF_8)))

  def now() = LocalDateTime.now()
}
