package spikes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import io.circe.generic.auto._
import io.circe.syntax._
import kamon.Kamon
import spikes.behavior.{Handlers, Reaper}
import spikes.model._
import spikes.validate.Validation

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object Main {

  sealed trait Message

  private final case class StartFailed(cause: Throwable) extends Message
  private final case class Started(binding: ServerBinding) extends Message
  private case object Stop extends Message

  val persistenceId: PersistenceId = PersistenceId.of("spikes", "1", "|")


  def main(args: Array[String]): Unit = {
    Kamon.init()
    ActorSystem[Message](Main("127.0.0.1", 8080), "spikes")
  }

  def apply(host: String, port: Int): Behavior[Message] = Behaviors.setup { ctx =>
    implicit val system = ctx.system

    val state = State(Users())
    val handlers = ctx.spawn(Handlers(state), "handlers")

    ctx.spawn(Reaper(handlers, 1.minute), "reaper")

    val routes: Route = handleRejections(Validation.rejectionHandler) {
      concat(
        UserRouter(handlers).route,
        InfoRouter(handlers).route,
        EntryRouter(handlers).route
      )
    }
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

final case class InfoRouter(handlers: ActorRef[Command])(implicit system: ActorSystem[_]) {

  implicit val ec: ExecutionContextExecutor = system.executionContext
  implicit val timeout: Timeout = 3.seconds

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  val route: Route = concat(
    (path("info") & get) {
      onSuccess(handlers.ask(Command.Info)) {
        case sr: StatusReply[Response.Info] =>
          complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, sr.getValue.asJson.toString())))
        case _ => complete(StatusCodes.BadRequest)
      }
    },
    (path("liveness") & get) {
      onSuccess(handlers.ask(Command.Info)) {
        case info: StatusReply[Response.Info] if info.isSuccess && info.getValue.recovered => complete(StatusCodes.OK)
        case _ => complete(StatusCodes.ServiceUnavailable)
      }
    },
    (path("readiness") & get) {
      onSuccess(handlers.ask(Command.Info)) {
        case info: StatusReply[Response.Info] if info.isSuccess && info.getValue.recovered => complete(StatusCodes.OK)
        case _ => complete(StatusCodes.ServiceUnavailable)
      }
    }
  )
}
