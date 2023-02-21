package spikes.validate

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.{complete, provide, reject}
import akka.http.scaladsl.server.{Directive1, Rejection, RejectionHandler, ValidationRejection}
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import wvlet.airframe.ulid.ULID

import java.time.LocalDate
import scala.util.{Failure, Success, Try}


final case class FieldRule[-T](field: String, isValid: T => Boolean, error: String)
final case class FieldErrorInfo(field: String, error: String)
final case class ModelValidationRejection(fields: Set[FieldErrorInfo]) extends Rejection

object Validation {
  private def caseClassFields[T <: Any](obj: AnyRef): Seq[(String, T)] = {
    obj.getClass.getDeclaredFields.toIndexedSeq.map { field =>
      field.setAccessible(true)
      (field.getName, field.get(obj).asInstanceOf[T])
    }
  }

  def validate[T, M <: Any](model: T, rules: Set[FieldRule[M]]): Set[FieldErrorInfo] = {
    val errorSet = scala.collection.mutable.Set[FieldErrorInfo]()
    val fields = caseClassFields(model.asInstanceOf[AnyRef])
    rules.map { rule =>
      fields.find(_._1 == rule.field) match {
        case None => throw new IllegalArgumentException(s"No such field for validation: ${rule.field}")
        case Some(pair) =>
          if (!rule.isValid(pair._2)) errorSet += FieldErrorInfo(rule.field, rule.error)
      }
    }
    errorSet.toSet[FieldErrorInfo]
  }

  def validated[T, M <: Any](model: T, rules: Set[FieldRule[M]]): Directive1[T] = {
    Try {
      validate(model, rules)
    } match {
      case Success(set) => if (set.isEmpty) provide(model) else reject(ModelValidationRejection(set))
      case Failure(e) => reject(ValidationRejection(e.getMessage))
    }
  }

  private def badreq(msg: String) = HttpResponse(BadRequest, entity = HttpEntity(`application/json`, msg))

  def rejectionHandler: RejectionHandler = RejectionHandler.newBuilder()
    .handle { case mvr@ModelValidationRejection(_) => complete(badreq(mvr.fields.asJson.toString())) }
    .handle { case vr: ValidationRejection         => complete(badreq(vr.message)) }
    .result()
}

object Regexes {
  val name = "^[\\p{L}\\p{Space}'-]+$"
  val email = "^([\\w-]+(?:\\.[\\w-]+)*)@\\w[\\w.-]+\\.[a-zA-Z]+$"
  val poco = "^[1-9][0-9]{3} ?[a-zA-Z]{2}$"
  val passw = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#&()–[{}]:;',?/*~$^+=<>]).{8,42}$"
}

object Rules {
  private val nameFieldRule = FieldRule("name", (name: String) => name.matches(Regexes.name), "invalid name")
  private val emailFieldRule = FieldRule("email", (email: String) => email.matches(Regexes.email), "invalid email address")
  private val passwordFieldRule = FieldRule("password", (password: String) => password.matches(Regexes.passw), "invalid password")
  private val bornFieldRules = Set(
    FieldRule("born", (born: LocalDate) => born.isBefore(LocalDate.now().minusYears(8)), "too young"),
    FieldRule("born", (born: LocalDate) => born.isAfter(LocalDate.now().minusYears(121)), "too old")
  )
  private val idFieldRule = FieldRule("id", (id: ULID) => id != null, "no id specified")

  val createUser: Set[FieldRule[LocalDate with String]] = Set(nameFieldRule, emailFieldRule, passwordFieldRule) ++ bornFieldRules
  val updateUser: Set[FieldRule[LocalDate with String with ULID]] = Set(nameFieldRule, passwordFieldRule, idFieldRule) ++ bornFieldRules
  val deleteUser: Set[FieldRule[String]] = Set(emailFieldRule)
  val login: Set[FieldRule[String]]      = Set(emailFieldRule, passwordFieldRule)
}
