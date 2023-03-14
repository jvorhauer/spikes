package spikes.behavior

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PreRestart, SupervisorStrategy}
import akka.pattern.StatusReply.{error, success}
import akka.persistence.typed.scaladsl.Effect.{none, persist}
import akka.persistence.typed.scaladsl.{EventSourcedBehavior, Recovery, ReplyEffect, RetentionCriteria}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted, RecoveryFailed, SnapshotSelectionCriteria}
import org.scalactic.TripleEquals.*
import spikes.model.{Command, Event, State, Task, User, Users, now}
import spikes.route.InfoRouter.{GetInfo, Info}
import wvlet.airframe.ulid.ULID

import scala.concurrent.duration.DurationInt


object Handlers {

  private val pid = PersistenceId.of("spikes", "3", "|")

  def apply(state: State = State(Users())): Behavior[Command] = Behaviors.setup { ctx =>
    var recovered = false

    val commandHandler: (State, Command) => ReplyEffect[Event, State] = (state, cmd) =>
      cmd match {
        case cu: User.Create =>
          state.findUser(cu.email) match {
            case Some(_) => none.thenReply(cu.replyTo)(_ => error(s"email ${cu.email} already in use"))
            case None    => persist(cu.asEvent).thenReply(cu.replyTo)(_ => success(cu.asResponse))
          }
        case uu: User.Update =>
          state.findUser(uu.id) match {
            case Some(_) => persist(uu.asEvent).thenReply(uu.replyTo)(us => success(us.findUser(uu.id).get.asResponse))
            case None    => none.thenReply(uu.replyTo)(_ => error(s"user with id ${uu.id} not found"))
          }
        case User.Remove(email, replyTo) =>
          state.findUser(email) match {
            case Some(user) => persist(User.Removed(user.id)).thenReply(replyTo)(_ => success(user.asResponse))
            case None       => none.thenReply(replyTo)(_ => error(s"user with email $email not found"))
          }

        case User.Login(email, passwd, replyTo) => state.findUser(email) match {
          case Some(user) if user.password === passwd =>
            persist(User.LoggedIn(user.id)).thenReply(replyTo) { state =>
              state.authorize(user.id) match {
                case Some(au) => success(au.asOAuthToken)
                case None     => error(s"!!!: user[${user.email}] is authenticated but no session found")
              }
            }
          case _ => none.thenReply(replyTo) { _ => error("invalid credentials") }
        }
        case User.Authorize(token, replyTo) => none.thenReply(replyTo)(_ => state.authorize(token))
        case User.Logout(token, replyTo) => state.authorize(token) match {
          case Some(us) => persist(User.LoggedOut(us.id)).thenReply(replyTo)(_ => success("Yeah"))
          case None => none.thenReply(replyTo)(_ => error("User was not logged in"))
        }

        case User.Find(id, replyTo) => state.getUserResponse(id) match {
          case Some(user) => none.thenReply(replyTo)(_ => success(user))
          case None => none.thenReply(replyTo)(_ => error(s"User $id not found"))
        }
        case User.All(replyTo) => none.thenReply(replyTo)(state => success(state.findUsers().map(_.asResponse)))

        case tc: Task.Create => state.findUser(tc.owner) match {
          case Some(_) => persist(tc.asEvent).thenReply(tc.replyTo)(_ => success(tc.asResponse))
          case None => none.thenReply(tc.replyTo)(_ => error(s"Owner ${tc.owner} for new Task not found"))
        }
        case tu: Task.Update => state.findTask(tu.id) match {
          case Some(_) => persist(tu.asEvent).thenReply(tu.replyTo)(us => success(us.findTask(tu.id).get.asResponse))
          case None => none.thenReply(tu.replyTo)(_ => error(s"Task ${tu.id} not found"))
        }

        case Reaper.Reap(replyTo) =>
          state.sessions.count(_.expires.isBefore(now)) match {
            case 0 => none.thenReply(replyTo)(_ => Reaper.Done)
            case count => persist(Reaper.Reaped(ULID.newULID, count)).thenReply(replyTo)(_ => Reaper.Done)
          }

        case GetInfo(replyTo) => none.thenReply(replyTo)(state => success(Info(state.users.size, state.sessions.size, state.tasks.size, recovered)))
      }

    val eventHandler: (State, Event) => State = (state, evt) =>
      evt match {
        case uc: User.Created => state.save(uc.asEntity)
        case uu: User.Updated => state.findUser(uu.id).map(u => state.save(u.copy(name = uu.name, password = uu.password, born = uu.born))).getOrElse(state)
        case ud: User.Removed => state.deleteUser(ud.id)

        case li: User.LoggedIn if li.expires.isAfter(now) => state.findUser(li.id).map(state.login(_, li.expires)).getOrElse(state)
        case lo: User.LoggedOut => state.logout(lo.id)

        case tc: Task.Created => state.save(tc.asTask)
        case tu: Task.Updated => state.save(tu.asTask)

        case _: Reaper.Reaped => state.copy(sessions = state.sessions.filter(_.expires.isAfter(now)))
      }


    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](pid, state, commandHandler, eventHandler)
      .withTagger(_ => Set("user"))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      .withRetention(RetentionCriteria.disabled)
      .withRecovery(Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.none))
      .receiveSignal {
        case (_, PreRestart) => ctx.log.info("pre-restart signal received")
        case (state, RecoveryCompleted) =>
          ctx.log.info(s"recovered: users: ${state.users.size}, sessions: ${state.sessions.size}, tasks: ${state.tasks.size}")
          recovered = true
        case (_, RecoveryFailed(t)) => ctx.log.error("recovery failed", t)
      }
  }
}
