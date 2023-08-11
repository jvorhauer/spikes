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
import scalikejdbc.{AutoSession, DBSession}
import spikes.model.{Command, Event, Note, SpikeSerializable, User}
import wvlet.airframe.ulid.ULID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object Manager {

  implicit val timeout: Timeout = 100.millis
  implicit val session: DBSession = AutoSession

  val pid: PersistenceId = PersistenceId.of("spikes", "13", "-")

  def lookup(str: String, key: ServiceKey[Command])(implicit system: ActorSystem[Nothing]): Future[Option[ActorRef[Command]]] =
    system.receptionist.ask(Find(key)).map(_.serviceInstances(key).find(_.path.name.contains(str)))
  def lookup(id: ULID, ctx: ActorContext[Command]): Future[Option[ActorRef[Command]]] = lookup(id)(ctx.system)
  def lookup(id: ULID)(implicit system: ActorSystem[Nothing]): Future[Option[ActorRef[Command]]] = lookup(id.toString, User.key)

  def apply(state: Manager.State = Manager.State(0)): Behavior[Command] = Behaviors.setup { ctx =>
    var recovered: Boolean = false

    val commandHandler: (Manager.State, Command) => Effect[Event, Manager.State] = (_, cmd) => cmd match {
      case uc: User.Create => User.Repository.find(uc.email) match {
        case Some(_) => Effect.reply(uc.replyTo)(StatusReply.error(s"email ${uc.email} already in use"))
        case None    => Effect.persist(uc.asEvent).thenRun(_ => lookup(uc.id, ctx).map(_.foreach(_.tell(uc))))
      }
      case ur: User.Remove => User.Repository.find(ur.id) match {
        case Some(_) => Effect.persist(ur.asEvent).thenRun(_ => lookup(ur.id, ctx).map(_.foreach(_.tell(ur))))
        case None    => Effect.reply(ur.replyTo)(StatusReply.error(s"user with id ${ur.id} not found"))
      }

      case GetInfo(replyTo) => Effect.reply(replyTo)(StatusReply.success(Info(recovered)))

      case what => Effect.unhandled.thenRun(_ => ctx.log.error(s"! Unhandled Command ${what} !"))
    }

    val eventHandler: (Manager.State, Event) => Manager.State = (state, evt) => evt match {
      case uc: User.Created =>
        ctx.spawn(User(User.State(uc)), User.name(uc.id, uc.email))
        User.Repository.save(uc)
        state.copy(users = state.users + 1)
      case ur: User.Removed =>
        lookup(ur.id, ctx).map(_.foreach(ctx.stop(_)))
        User.Repository.remove(ur.id)
        state.copy(users = state.users - 1)
    }

    EventSourcedBehavior(pid, state, commandHandler, eventHandler)
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      .withRetention(RetentionCriteria.disabled)
      .withRecovery(Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.none))
      .withTagger(_ => Set("users", User.tag))
      .receiveSignal {
        case (_, RecoveryCompleted) =>
          ctx.log.info(s"recovered: users: ${User.Repository.size()}, notes: ${Note.Repository.size()}")
          recovered = true
        case (_, RecoveryFailed(t)) => ctx.log.error("recovery failed", t)
      }
  }

  final case class State(users: Int) extends SpikeSerializable

  final case class GetInfo(replyTo: ActorRef[StatusReply[Info]]) extends Command
  final case class Info(users: Int, notes: Int, recovered: Boolean) extends SpikeSerializable
  object Info {
    def apply(recovered: Boolean): Info = new Info(
      User.Repository.size(), Note.Repository.size(), recovered
    )
  }
}
