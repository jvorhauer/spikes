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
import spikes.build.BuildInfo
import spikes.model.{Command, Event, Note, SpikeSerializable, User}
import wvlet.airframe.ulid.ULID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

object Manager {

  implicit val timeout: Timeout = 100.millis
  implicit val session: DBSession = AutoSession

  val pid: PersistenceId = PersistenceId.of("spikes", "22", "-")

  type LookupResult = Future[Option[ActorRef[Command]]]

  def lookup(str: String, key: ServiceKey[Command])(implicit system: ActorSystem[Nothing]): LookupResult =
    system.receptionist.ask(Find(key)).map(_.serviceInstances(key).find(_.path.name.contains(str)))
  def lookup(id: ULID, ctx: ActorContext[Command])                          : LookupResult = lookup(id)(ctx.system)
  def lookup(id: ULID)(implicit system: ActorSystem[Nothing])               : LookupResult = lookup(id.toString, User.key)
  def lookup(id: ULID, key: ServiceKey[Command], ctx: ActorContext[Command]): LookupResult = lookup(id.toString, key)(ctx.system)


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
      case IsReady(replyTo) => Effect.reply(replyTo)(StatusReply.success(recovered))
      case Check(replyTo)   => Effect.reply(replyTo)(StatusReply.success(Checked(check(ctx.system))))
    }

    val eventHandler: (Manager.State, Event) => Manager.State = (state, evt) => evt match {
      case uc: User.Created =>
        ctx.spawn(User(User.Repository.save(uc)), User.name(uc.id, uc.email))
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
          ctx.log.info(s"recovered: users: ${User.Repository.size()} (${state.users}), notes: ${Note.Repository.size()}")
          recovered = true
        case (_, RecoveryFailed(t)) => ctx.log.error("recovery failed", t)
      }
  }

  def check(implicit system: ActorSystem[Nothing]): Boolean = {
    User.Repository.list(limit = Int.MaxValue).forall(us => Await.result(lookup(us.id.toString, User.key), 100.millis).isDefined) &&
    Note.Repository.list(limit = Int.MaxValue).forall(ns => Await.result(lookup(ns.id.toString, Note.key), 100.millis).isDefined)
  }

  final case class State(users: Int) extends SpikeSerializable

  final case class Check  (replyTo: ActorRef[StatusReply[Checked]]) extends Command
  final case class GetInfo(replyTo: ActorRef[StatusReply[Info]])    extends Command
  final case class IsReady(replyTo: ActorRef[StatusReply[Boolean]]) extends Command

  final case class Info(users: Int, notes: Int, recovered: Boolean, version: String = BuildInfo.version, built: String = BuildInfo.buildTime) extends SpikeSerializable
  object Info {
    def apply(recovered: Boolean): Info = new Info(User.Repository.size(), Note.Repository.size(), recovered)
  }
  final case class Checked(ok: Boolean) extends SpikeSerializable
}
