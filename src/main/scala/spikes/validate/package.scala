package spikes

import io.lemonlabs.uri.AbsoluteUrl
import org.owasp.encoder.Encode

import java.time.{LocalDate, LocalDateTime}
import scala.util.matching.Regex

package object validate {

  private def ldnow = LocalDate.now()

  val rname: Regex = "^[\\p{L}\\s'-]+$".r
  val remail: Regex = "^([\\w-]+(?:\\.[\\w\\d-]+)*)@\\w[\\w\\d.-]+\\.[a-zA-Z]+$".r
  val rpassw: Regex = "^(?=.*?[A-Z])(?=.*?[a-z])(?=.*?[0-9])(?=.*?[#?!@$%^&*-]).{8,}$".r
  val rurl: Regex = "^https?://\\w[\\w\\d.-_]/.*$".r
  val rtitle: Regex = "^[\\p{L}\\s\\d\\W]+$".r
  val rbody: Regex = "^[\\p{L}\\s\\d\\W]+$".r

  case class Rule[T](name: String, isValid: T => Boolean, msg: String)

  private val clean: String => String = s => Option(s).map(_.trim).map(Encode.forHtml).getOrElse("")

  val nameRule: String => Rule[String] = s => Rule("name", s => rname.matches(clean(s)), s"\"$s\" is not a valid name")
  val emailRule: String => Rule[String] = e => Rule("email", e => remail.matches(e), s"\"$e\" is not a valid email address")
  val passwordRule: String => Rule[String] = p => Rule("password", p => rpassw.matches(p), "***** is not a valid password")
  val bornRule: LocalDate => Rule[LocalDate] = ld => Rule("born", ld => ld.isBefore(ldnow.minusYears(8)) && ld.isAfter(ldnow.minusYears(123)), s"$ld is not a valid date")
  
  val dueRule: LocalDateTime => Rule[LocalDateTime] = ldt => Rule("due", ldt => ldt.isAfter(LocalDateTime.now()), s"due is $ldt, but should be in the future")
  val titleRule: String => Rule[String] = s => Rule("title", s => rtitle.matches(clean(s)), s"\"${s}\" is not a valid title")
  val bodyRule: String => Rule[String] = s => Rule("body", s => rbody.matches(clean(s)), s"\"${s}\" is not a valid body")
  val urlRule: String => Rule[String] = s => Rule("url", s => AbsoluteUrl.parseTry(s).isSuccess, s"\"${s}\" is not a valid URL")
}
