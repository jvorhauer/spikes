package spikes.validate

sealed trait Validated[+E, +S]
object Validated {
  final case class Passed[S](value: S)        extends Validated[Nothing, S]
  final case class Failed[E](errors: List[E]) extends Validated[E, Nothing]
}
