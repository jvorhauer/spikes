package spikes.model

object Status extends Enumeration {
  type Status = Value
  val Blank, New, ToDo, Doing, Completed = Value
}
