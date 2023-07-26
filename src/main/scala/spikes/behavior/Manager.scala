package spikes.behavior

import akka.actor.typed.receptionist.Receptionist.Find
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, Recovery, RetentionCriteria}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted, RecoveryFailed, SnapshotSelectionCriteria}
import akka.util.Timeout
import spikes.model.User.UserId
import spikes.model.{Command, Event, User}
import wvlet.airframe.ulid.ULID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object Manager {

  implicit val timeout: Timeout = 100.millis

  val pid: PersistenceId = PersistenceId.of("spikes", "11", "-")

  def lookup(str: String, key: ServiceKey[Command])(implicit system: ActorSystem[Nothing]): Future[Option[ActorRef[Command]]] =
    system.receptionist.ask(Find(key)).map(_.serviceInstances(key).find(_.path.name.contains(str)))

  def lookup(id: ULID, ctx: ActorContext[Command]): Future[Option[ActorRef[Command]]] = lookup(id)(ctx.system)
  def lookup(id: ULID)(implicit system: ActorSystem[Nothing]): Future[Option[ActorRef[Command]]] = lookup(id.toString, User.key)


  def apply(state: Manager.State = Manager.State()): Behavior[Command] = Behaviors.setup { ctx =>
    var recovered: Boolean = false

    val commandHandler: (Manager.State, Command) => Effect[Event, Manager.State] = (state, cmd) =>
      cmd match {
        case uc: User.Create =>
          if (state.find(uc.email)) {
            Effect.reply(uc.replyTo)(StatusReply.error(s"email ${uc.email} already in use"))
          } else {
            Effect.persist(uc.asEvent).thenReply(uc.replyTo)(_ => StatusReply.success(uc.asResponse))
          }
        case ur: User.Remove =>
          if (state.find(ur.id)) {
            Effect.persist(ur.asEvent).thenRun(_ => lookup(ur.id, ctx).map(_.foreach(_.tell(ur))))
          } else {
            Effect.reply(ur.replyTo)(StatusReply.error(s"user with id ${ur.id} not found"))
          }

        case GetInfo(replyTo) => Effect.reply(replyTo)(StatusReply.success(Info(state.uc, recovered)))

        case _ => Effect.unhandled
      }

    val eventHandler: (Manager.State, Event) => Manager.State = (state, evt) =>
      evt match {
        case uc: User.Created =>
          ctx.spawn(User(uc.asState), User.name(uc.id, uc.email))
          state.copy(users = state.users.concat(Map(uc.id.toString -> uc.email, uc.email -> uc.id.toString)))
        case ur: User.Removed =>
          lookup(ur.id, ctx).map(_.foreach(ctx.stop(_)))
          state.copy(users = state.users.removedAll(List(state.getEmailForId(ur.id), ur.id.toString)))
      }

    EventSourcedBehavior(pid, state, commandHandler, eventHandler)
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1)
      )
      .withRetention(RetentionCriteria.disabled)
      .withRecovery(Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.none))
      .receiveSignal {
        case (state, RecoveryCompleted) =>
          ctx.log.info(s"recovered: users: ${state.users.size}")
          recovered = true
        case (_, RecoveryFailed(t)) => ctx.log.error("recovery failed", t)
      }
  }

  final case class State(users: Map[String, String] = Map.empty) {
    lazy val uc: Int = users.size / 2

    def find(id: UserId) : Boolean = find(id.toString)
    def find(key: String): Boolean = users.contains(key)

    def getEmailForId(id: UserId): String = users.getOrElse(id.toString, "")
  }

  // TODO: move to Projection based implementation!!!!
  final case class Info(users: Int, recovered: Boolean)
  final case class GetInfo(replyTo: ActorRef[StatusReply[Info]]) extends Command
}
