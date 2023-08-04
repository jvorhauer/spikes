package spikes.behavior

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import spikes.model.{Event, User}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class ProjectionHandler(tag: String, system: ActorSystem[_]) extends Handler[EventEnvelope[Event]]() {

  private implicit val ec: ExecutionContext = system.executionContext
  private var counter = 0

  private def increase: Future[Done] = {
    counter = counter + 1
    println(s"project handler: increased to $counter")
    Future.successful(Done)
  }

  private def decrease: Future[Done] = {
    counter = counter - 1
    println(s"project handler: decreased to $counter")
    Future.successful(Done)
  }

  override def process(envelope: EventEnvelope[Event]): Future[Done] = {
    val processed = envelope.event match {
      case _: User.Created => increase
      case _: User.Removed => decrease
      case _ => Future.successful(Done)
    }
    processed.onComplete {
      case Success(_) => println("projection handler: onComplete: done")
      case _ => ()
    }
    processed
  }
}
