package spikes

import akka.actor.typed.eventstream.EventStream.Subscribe
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal
import akka.persistence.typed.PersistenceId
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.slick.SlickProjection
import akka.projection.{ProjectionBehavior, ProjectionId}
import slick.basic.DatabaseConfig
import slick.jdbc.H2Profile
import spikes.behavior.{Handlers, Reader, Reaper}
import spikes.model.{Event, State, UserRoutes, Users}
import spikes.projection.{UsersRepository, UsersRepositoryHandler}

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object Main {

  sealed trait Message

  private final case class StartFailed(cause: Throwable) extends Message
  private final case class Started(binding: ServerBinding) extends Message
  private case object Stop extends Message

  val persistenceId = PersistenceId.ofUniqueId("spikes-handlers")
  val projectionId: ProjectionId = ProjectionId("users", "user")

  def main(args: Array[String]): Unit = {
    ActorSystem[Message](Main("127.0.0.1", 8080), "spikes")
  }

  def apply(host: String, port: Int): Behavior[Message] = Behaviors.setup { ctx =>
    implicit val system = ctx.system
    implicit val ec = system.executionContext

    val state = State(Users())

    val reader = ctx.spawn(Reader(), "reader")
    ctx.system.eventStream.tell(Subscribe(reader))
    val handlers = ctx.spawn(Handlers(state), "handlers")

    ctx.spawn(Reaper(handlers, 1.hour), "reaper")
    val query = ctx.spawn(Reader.query(), "query")

    val dbConfig: DatabaseConfig[H2Profile] = DatabaseConfig.forConfig("h2projection")
    val repo = new UsersRepository(dbConfig)
    repo.create()

    val sourceProvider = EventSourcedProvider.eventsByTag[Event](
        system              = system,
        readJournalPluginId = CassandraReadJournal.Identifier,
        tag                 = "user")
    val projection = SlickProjection.exactlyOnce(
      projectionId   = projectionId,
      sourceProvider = sourceProvider,
      databaseConfig = dbConfig,
      handler        = () => new UsersRepositoryHandler(repo)
    )
    ctx.spawn(ProjectionBehavior(projection), projection.projectionId.id)

    val routes = UserRoutes(handlers, query).route
    val serverBinding = Http().newServerAt(host, port).bind(routes)
    ctx.pipeToSelf(serverBinding) {
      case Success(binding) => Started(binding)
      case Failure(exception) => StartFailed(exception)
    }

    def running(binding: ServerBinding): Behavior[Message] =
      Behaviors.receiveMessagePartial[Message] {
        case Stop =>
          ctx.log.info(s"Stopping: ${binding.localAddress.getHostString}:${binding.localAddress.getPort}")
          Behaviors.stopped
      }.receiveSignal {
        case (_, PostStop) =>
          binding.unbind()
          Behaviors.same
      }

    def starting(wasStopped: Boolean): Behaviors.Receive[Message] =
      Behaviors.receiveMessage[Message] {
        case StartFailed(t) => throw new RuntimeException("Server failed to start", t)
        case Started(binding) =>
          ctx.log.info(s"Started: ${binding.localAddress.getHostString}:${binding.localAddress.getPort}")
          if (wasStopped) ctx.self ! Stop
          running(binding)
        case Stop => starting(wasStopped = true)
      }

    starting(wasStopped = false)
  }
}
