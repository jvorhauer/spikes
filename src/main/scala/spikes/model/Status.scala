package spikes.model

object Status extends Enumeration {
  type Status = Value
  val Blank, New, Ready, ToDo, Doing, Review, Completed = Value
}
