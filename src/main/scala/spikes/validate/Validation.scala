package spikes.validate

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, provide, reject}
import akka.http.scaladsl.server.{Directive1, Rejection, RejectionHandler}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import io.circe.syntax.EncoderOps
import spikes.model.Request


object Validation {

  case class FieldErrorInfo(field: String, error: String)
  case class ModelValidationRejection(fields: Set[FieldErrorInfo]) extends Rejection

  def validate[T](rule: Rule[T], value: T, name: String): Option[FieldErrorInfo] = if (rule.isValid(value)) None else Some(FieldErrorInfo(name, rule.msg))

  def validated[T <: Request](model: T): Directive1[T] = {
    model.validated match {
      case set if set.nonEmpty => reject(ModelValidationRejection(set))
      case _                   => provide(model)
    }
  }

  def rejectionHandler: RejectionHandler = RejectionHandler.newBuilder()
    .handle { case mvr@ModelValidationRejection(_) => complete(StatusCodes.BadRequest, mvr.fields.asJson) }
    .result()
}
