package spikes.validate

import akka.http.scaladsl.server.Directives.{provide, reject}
import akka.http.scaladsl.server.{Directive1, Rejection, ValidationRejection}

import scala.util.{Failure, Success, Try}

case class FieldRule[-T](field: String, isValid: T => Boolean, error: String)
case class FieldErrorInfo(field: String, error: String)
case class ModelValidationRejection(fields: Set[FieldErrorInfo]) extends Rejection

object ModelValidation {
  private def caseClassFields[T <: Any](obj: AnyRef): Seq[(String, T)] = {
    obj.getClass.getDeclaredFields.map { field =>
      field.setAccessible(true)
      (field.getName, field.get(obj).asInstanceOf[T])
    }
  }

  def validateModel[T, M <: Any](model: T, rules: Set[FieldRule[M]]): Directive1[T] = {
    val errorSet = scala.collection.mutable.Set[FieldErrorInfo]()
    val fields = caseClassFields(model.asInstanceOf[AnyRef])
    Try {
      rules.map { rule =>
        fields.find(_._1 == rule.field) match {
          case None => throw new IllegalArgumentException(s"No such field for validation: ${rule.field}")
          case Some(pair) =>
            if (!rule.isValid(pair._2)) errorSet += FieldErrorInfo(rule.field, rule.error)
        }
      }
      errorSet.toSet[FieldErrorInfo]
    } match {
      case Success(set) => if (set.isEmpty) provide(model) else reject(ModelValidationRejection(set))
      case Failure(e) => reject(ValidationRejection(e.getMessage))
    }
  }
}
