package spikes.validate

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, provide, reject}
import akka.http.scaladsl.server.{Directive1, Rejection, RejectionHandler}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import io.circe.syntax.EncoderOps
import spikes.model.Request

import java.time.{LocalDate, LocalDateTime}


object Validation {

  final case class ErrorInfo(field: String, error: String)
  final case class ModelValidationRejection(fields: Set[ErrorInfo]) extends Rejection

  def validate[T](rule: Rule[T], value: T, name: String): Option[ErrorInfo] = if (rule.isValid(value)) None else Some(ErrorInfo(name, rule.msg))
  def validate(name: String, value: String): Option[ErrorInfo] = rulers.get(name).orElse(Some(trueRule)).flatMap(rule => validate(rule(value), value, name))
  def validate(name: String, value: LocalDate): Option[ErrorInfo] = rulerld.get(name).orElse(Some(trueRule)).flatMap(rule => validate(rule(value), value, name))
  def validate(name: String, value: LocalDateTime): Option[ErrorInfo] = rulerldt.get(name).orElse(Some(trueRule)).flatMap(rule => validate(rule(value), value, name))
  def validate(name: String, value: Option[String]): Option[ErrorInfo] = ruleros.get(name).orElse(Some(trueRule)).flatMap(rule => validate(rule(value), value, name))
  def validate(name: String, value: Int): Option[ErrorInfo] = ruleri.get(name).orElse(Some(trueRule)).flatMap(rule => validate(rule(value), value, name))

  def validated[T <: Request](model: T): Directive1[T] = model.validated match {
    case set if set.nonEmpty => reject(ModelValidationRejection(set))
    case _                   => provide(model)
  }

  def rejectionHandler: RejectionHandler = RejectionHandler.newBuilder().handle {
      case mvr@ModelValidationRejection(_) => complete(StatusCodes.BadRequest, mvr.fields.asJson)
    }.result()
}
