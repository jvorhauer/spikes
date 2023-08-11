package spikes.behavior

import akka.Done
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import org.slf4j.LoggerFactory
import spikes.model.{Event, User}
import wvlet.airframe.ulid.ULID

import scala.collection.mutable
import scala.concurrent.Future

class ProjectionHandler extends Handler[EventEnvelope[Event]]() {

  private val logger = LoggerFactory.getLogger(getClass)
//  private implicit val ec: ExecutionContext = system.executionContext
  private var counter = 0
  private val users: mutable.Map[ULID, User.Created] = mutable.CollisionProofHashMap.empty

  private def increase: Future[Done] = {
    counter = counter + 1
    logger.info(": increase: to {}", counter)
    Future.successful(Done)
  }

  private def decrease: Future[Done] = {
    counter = counter - 1
    logger.info("decrease: to {}", counter)
    Future.successful(Done)
  }

  override def process(envelope: EventEnvelope[Event]): Future[Done] = {
    logger.info("process: called with {}", envelope)
    envelope.event match {
      case uc: User.Created =>
        users.put(uc.id, uc)
        increase
      case ur: User.Removed =>
        users.remove(ur.id)
        decrease
      case ul: User.LoggedIn => logger.info("user {} logged in", ul.id)
      case _ => Future.successful(Done)
    }
//    processed.onComplete {
//      case Success(_) => logger.info("process: onComplete: done")
//      case _ => logger.error("process: error!")
//    }
    Future.successful(Done)
  }
}
