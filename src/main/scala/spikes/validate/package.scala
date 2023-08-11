package spikes

import io.lemonlabs.uri.AbsoluteUrl
import org.owasp.encoder.Encode

import java.time.{LocalDate, LocalDateTime}
import scala.util.matching.Regex

package object validate {

  private def ldnow = LocalDate.now()

  val rname: Regex = "^[\\p{L}\\s'-]+$".r
  val remail: Regex = "^([\\w-]+(?:\\.[\\w-]+)*)@\\w[\\w.-]+\\.[a-zA-Z]+$".r
  val rpassw: Regex = "^(?=.*?[A-Z])(?=.*?[a-z])(?=.*?[0-9])(?=.*?[#?!@$%^&*-]).{8,}$".r
  val rurl: Regex = "^https?://\\w[\\w.-_]/.*$".r
  val rtitle: Regex = "^[\\p{L}\\s\\d\\W]+$".r
  val rbody: Regex = "^[\\p{L}\\s\\d\\W]+$".r
  val rslug: Regex = "^\\d{8}-[\\p{L}\\d\\W]+$".r

  final case class Rule[T](name: String, isValid: T => Boolean, msg: String)

  private val clean: String => String = s => Option(s).map(_.trim).map(Encode.forHtml).getOrElse("")

  val trueRule: Any => Rule[Any] = _ => Rule("???", _ => true, "always okay")
  val nameRule: String => Rule[String] = s => Rule("name", s => rname.matches(clean(s)), s"\"$s\" is not a valid name")
  val emailRule: String => Rule[String] = e => Rule("email", e => remail.matches(e), s"\"$e\" is not a valid email address")
  val passwordRule: String => Rule[String] = _ => Rule("password", p => rpassw.matches(p), "***** is not a valid password")
  val bornRule: LocalDate => Rule[LocalDate] = ld => Rule("born", ld => ld.isBefore(ldnow.minusYears(8)) && ld.isAfter(ldnow.minusYears(123)), s"$ld is not a valid date")

  val dueRule: LocalDateTime => Rule[LocalDateTime] = ldt => Rule("due", ldt => ldt.isAfter(LocalDateTime.now()), s"due is $ldt, but should be in the future")
  val titleRule: String => Rule[String] = s => Rule("title", s => rtitle.matches(clean(s)), s"\"${s}\" is not a valid title")
  val bodyRule: String => Rule[String] = s => Rule("body", s => rbody.matches(clean(s)), s"\"${s}\" is not a valid body")
  val urlRule: String => Rule[String] = s => Rule("url", s => AbsoluteUrl.parseTry(s).isSuccess, s"\"${s}\" is not a valid URL")
  val slugRule: String => Rule[String] = s => Rule("slug", s => rslug.matches(clean(s)), s"\"$s\" is not a valid note slug")

  val rulers: Map[String, String => Rule[String]] = Map(
    "name" -> nameRule,
    "email" -> emailRule,
    "password" -> passwordRule,
    "title" -> titleRule,
    "body" -> bodyRule,
    "url" -> urlRule,
    "slug" -> slugRule
  )
  val rulerld: Map[String, LocalDate => Rule[LocalDate]] = Map("born" -> bornRule)
  val rulerldt: Map[String, LocalDateTime => Rule[LocalDateTime]] = Map("due" -> dueRule)
}
