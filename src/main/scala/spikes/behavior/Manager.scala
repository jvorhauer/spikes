package spikes.behavior

import akka.actor.typed.receptionist.Receptionist.Find
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted, RecoveryFailed}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import scalikejdbc.{AutoSession, DBSession}
import spikes.behavior.SessionReaper.{Reap, Reaped}
import spikes.build.BuildInfo
import spikes.model.{Command, Event, Note, SPID, Session, Status, Tag, User, next}

import scala.collection.immutable.SortedSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object Manager {

  private val cfg: Config = ConfigFactory.defaultApplication()

  implicit val timeout: Timeout = 100.millis
  implicit val session: DBSession = AutoSession

  val pid: PersistenceId = PersistenceId("spikes", cfg.getString("spikes.persistence.version"))

  type LookupResult = Future[Option[ActorRef[Command]]]

  def lookup(str: String, key: ServiceKey[Command])(implicit system: ActorSystem[Nothing]): LookupResult =
    system.receptionist.ask(Find(key)).map(_.serviceInstances(key).find(_.path.name.contains(str)))
  def lookup(id: SPID, ctx: ActorContext[Command])                          : LookupResult = lookup(id)(ctx.system)
  def lookup(id: SPID)(implicit system: ActorSystem[Nothing])               : LookupResult = lookup(id.toString, User.key)
  def lookup(id: SPID, key: ServiceKey[Command], ctx: ActorContext[Command]): LookupResult = lookup(id.toString, key)(ctx.system)


  def apply(state: Manager.State = Manager.State(0, 0)): Behavior[Command] = Behaviors.setup { ctx =>
    var recovered: Boolean = false

    val commandHandler: (Manager.State, Command) => Effect[Event, Manager.State] = (_, cmd) => cmd match {
      case uc: User.Create => User.find(uc.email) match {
        case Some(_) => Effect.reply(uc.replyTo)(StatusReply.error(s"email ${uc.email} already in use"))
        case None    => Effect.persist(uc.asEvent).thenRun(_ => lookup(uc.id, ctx).map(_.foreach(_.tell(uc))))
      }
      case ur: User.Remove => User.find(ur.id) match {
        case Some(_) => Effect.persist(ur.asEvent).thenRun(_ => lookup(ur.id, ctx).map(_.foreach(_.tell(ur))))
        case None    => Effect.reply(ur.replyTo)(StatusReply.error(s"user with id ${ur.id} not found"))
      }

      case tc: Tag.Create => Tag.find(tc.title) match {
        case Some(_) => Effect.reply(tc.replyTo)(StatusReply.error(s"tag with title ${tc.title} already exists"))
        case None => Effect.persist(tc.toEvent).thenReply(tc.replyTo)(_ => StatusReply.success(tc.toResponse))
      }
      case tu: Tag.Update => Tag.find(tu.id) match {
        case Some(_) => Effect.persist(tu.toEvent).thenReply(tu.replyTo)(_ => StatusReply.success(tu.toResponse))
        case None => Effect.reply(tu.replyTo)(StatusReply.error(s"tag with id ${tu.id} not found, so not updated"))
      }
      case tr: Tag.Remove => Tag.find(tr.id) match {
        case Some(t) => Effect.persist(tr.toEvent).thenReply(tr.replyTo)(_ => StatusReply.success(t.toResponse))
        case None => Effect.reply(tr.replyTo)(StatusReply.error(s"tag with id ${tr.id} not found, so not deleted"))
      }

      case GetInfo(replyTo) => Effect.reply(replyTo)(StatusReply.success(Info(recovered)))
      case IsReady(replyTo) => Effect.reply(replyTo)(StatusReply.success(recovered))
      case Check(replyTo)   => Effect.reply(replyTo)(StatusReply.success(Checked(check(ctx.system))))
      case GetStati(replyTo) => Effect.reply(replyTo)(StatusReply.success(StatusValues()))

      case Reap(replyTo)    => Session.expired match {
        case 0     => Effect.reply(replyTo)(SessionReaper.Done)
        case count =>
          ctx.log.info(s"Reap: $count")
          Effect.persist(Reaped(next, count)).thenReply(replyTo)(_ => SessionReaper.Done)
      }
    }

    val eventHandler: (Manager.State, Event) => Manager.State = (state, evt) => evt match {
      case uc: User.Created =>
        ctx.spawn(User(User.save(uc)), User.name(uc.id, uc.email))
        state.copy(users = state.users + 1)
      case ur: User.Removed =>
        lookup(ur.id, ctx).map(_.foreach(ctx.stop))
        User.remove(ur.id)
        state.copy(users = state.users - 1)

      case tc: Tag.Created =>
        tc.save
        state.copy(tags = state.tags + 1)
      case tu: Tag.Updated =>
        tu.save
        state
      case tr: Tag.Removed =>
        Tag.remove(tr.id)
        state.copy(tags = state.tags - 1)

      case _: Reaped =>
        Session.reap()
        state
    }

    EventSourcedBehavior(pid, state, commandHandler, eventHandler)
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))
      .withTagger(_ => Set("users", User.tag))
      .receiveSignal {
        case (state, RecoveryCompleted) =>
          ctx.log.info(s"recovery completed: ${state.users} users")
          recovered = true
        case (_, rf: RecoveryFailed) => ctx.log.error("recovery failed", rf.failure)
      }
  }

  def check(implicit system: ActorSystem[Nothing]): Boolean = {
    User.list(limit = Int.MaxValue).forall(us => Await.result(lookup(us.id.toString, User.key), 100.millis).isDefined) &&
    Note.list(limit = Int.MaxValue).forall(ns => Await.result(lookup(ns.id.toString, Note.key), 100.millis).isDefined)
  }

  final case class State(users: Int, tags: Int) extends Serializable

  final case class Check  (replyTo: ActorRef[StatusReply[Checked]]) extends Command
  final case class GetInfo(replyTo: ActorRef[StatusReply[Info]])    extends Command
  final case class IsReady(replyTo: ActorRef[StatusReply[Boolean]]) extends Command

  final case class Info(
      recovered: Boolean,
      users   : Int    = User.size,
      notes   : Int    = Note.size,
      sessions: Int    = Session.size,
      tags    : Int    = Tag.size,
      version : String = BuildInfo.version,
      built   : String = BuildInfo.buildTime,
      persid  : String = Manager.pid.id
  ) extends Serializable
  final case class Checked(ok: Boolean) extends Serializable

  final case class GetStati(replyTo: ActorRef[StatusReply[StatusValues]]) extends Command
  final case class StatusValues(stati: SortedSet[String] = Status.values.map(_.toString)) extends Serializable
}
