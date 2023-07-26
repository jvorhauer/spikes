package spikes.validate

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, provide, reject}
import akka.http.scaladsl.server.{Directive1, Rejection, RejectionHandler}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import io.circe.syntax.EncoderOps
import spikes.model.Request


object Validation {

  final case class ErrorInfo(field: String, error: String)
  final case class ModelValidationRejection(fields: Set[ErrorInfo]) extends Rejection
  final case class ItemValidationRejection(error: String) extends Rejection

  def validate[T](rule: Rule[T], value: T, name: String): Option[ErrorInfo] = if (rule.isValid(value)) None else Some(ErrorInfo(name, rule.msg))

  def validated[T <: Request](model: T): Directive1[T] = {
    model.validated match {
      case set if set.nonEmpty => reject(ModelValidationRejection(set))
      case _                   => provide(model)
    }
  }

  def validated[T <: Request](item: Either[String, T]): Directive1[T] = {
    item match {
      case Left(s)    => reject(ItemValidationRejection(s))
      case Right(req) => provide(req)
    }
  }

  def rejectionHandler: RejectionHandler = RejectionHandler.newBuilder().handle {
      case mvr@ModelValidationRejection(_) => complete(StatusCodes.BadRequest, mvr.fields.asJson)
      case ivr@ItemValidationRejection(_) => complete(StatusCodes.BadRequest, ivr)
    }.result()
}
