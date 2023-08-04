package spikes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.*
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.projection.cassandra.scaladsl.CassandraProjection
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.SourceProvider
import kamon.Kamon
import spikes.behavior.{Manager, ProjectionHandler}
import spikes.model.{Event, User}
import spikes.route.*
import spikes.validate.Validation


object Spikes {
  def main(args: Array[String]): Unit = {
    Kamon.init()
    ActorSystem[Nothing](apply(), "spikes")
  }

  def apply(port: Int = 8080): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
    implicit val system = ctx.system

    val manager = ctx.spawn(Manager(), "manager")

    val sp: SourceProvider[Offset, EventEnvelope[Event]] =
      EventSourcedProvider.eventsByTag[Event](system, readJournalPluginId = CassandraReadJournal.Identifier, tag = User.tag)
    val projection =
      CassandraProjection.atLeastOnce(ProjectionId("users", User.tag), sp, () => new ProjectionHandler(User.tag, system))
    ctx.spawn(ProjectionBehavior(projection), projection.projectionId.id)

    val routes = handleRejections(Validation.rejectionHandler) {
      concat(
        ManagerRouter(manager).route,
        InfoRouter(manager).route,
        NotesRouter().route
      )
    }
    Http(system).newServerAt("0.0.0.0", port).bind(routes)
    Behaviors.empty
  }
}
