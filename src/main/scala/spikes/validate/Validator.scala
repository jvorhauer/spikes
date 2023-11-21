package spikes.validate

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, provide, reject}
import akka.http.scaladsl.server.{Directive1, Rejection, RejectionHandler}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import io.circe.syntax.EncoderOps
import spikes.model.{Request, clean}
import spikes.validate.Validated.{Failed, Passed}
import spikes.validate.Validator.PredicateMeta

trait Validator[+E, S] { previous =>
  protected def input: S
  protected def failedPredicates[E1 >: E]: List[PredicateMeta[E1, S]] = List.empty

  def satisfying[E1 >: E](f: S => Boolean, failureReason: E1): Validator[E1, S] = new Validator[E1, S] {
    override def input: S = previous.input
    override def failedPredicates[E2 >: E1]: List[PredicateMeta[E2, S]] =
      if (f(input)) previous.failedPredicates else PredicateMeta[E2, S](f, failureReason) :: previous.failedPredicates
  }

  def applied: Validated[E, S] = failedPredicates match {
    case Nil => Validated.Passed(input)
    case _ => Validated.Failed(failedPredicates.map[E](_.failureReason).reverse)
  }
}
object Validator {
  def apply[E, S](s: S): Validator[E, S] = new Validator[E, S] {
    override def input: S = s
  }

  final case class PredicateMeta[+E, S](f: S => Boolean, failureReason: E)


  trait ValidationError {
    def entity: String
    def field: String
    def value: String
    def error: String = "invalid"
    def toErrorInfo: ErrorInfo = ErrorInfo(s"${entity}.${field}", s"${error}: ${if (value.isEmpty) "<empty>" else clean(value)}")
  }
  final case class ErrorInfo(field: String, error: String)
  final case class RequestValidationRejection(fields: List[ErrorInfo]) extends Rejection

  def validated[T<: Request](req: T): Directive1[T] = req.validated match {
    case Passed(_) => provide(req)
    case Failed(errors) => reject(RequestValidationRejection(errors.map(_.toErrorInfo)))
  }

  def rejectionHandler: RejectionHandler = RejectionHandler.newBuilder().handle {
    case RequestValidationRejection(errors) => complete(StatusCodes.BadRequest, errors.asJson)
  }.result()
}
