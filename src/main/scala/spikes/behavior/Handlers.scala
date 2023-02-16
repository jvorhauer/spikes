package spikes.behavior

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PreRestart, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import akka.persistence.typed.{RecoveryCompleted, RecoveryFailed}
import spikes.Main.persistenceId
import spikes.db.Repository
import spikes.model.Command.Done
import spikes.model._
import wvlet.airframe.ulid.ULID

import scala.concurrent.duration._

object Handlers {

  private var recovered: Boolean = false

  def apply(state: State = State(Users())): Behavior[Command] = Behaviors.setup { ctx =>
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      persistenceId = persistenceId,
      emptyState = state,
      commandHandler = commandHandler,
      eventHandler = eventHandler
    ).withEventPublishing(false) // I find the published events of little use for anything...
      .withTagger(_ => Set("user"))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      .receiveSignal {
        case (_, PreRestart) => ctx.log.info("pre-restart signal received")
        case (state, RecoveryCompleted) =>
          ctx.log.info(s"recovered: users: ${state.users.size}, sessions: ${state.sessions.size} and entries: ${state.entries.size}")
          recovered = true
        case (_, RecoveryFailed(t)) => ctx.log.error("recovery failed", t)
      }
  }

  private val commandHandler: (State, Command) => ReplyEffect[Event, State] = { (state, cmd) =>
    cmd match {
      case cu: Command.CreateUser =>
        state.find(cu.email) match {
          case Some(_) => Effect.none.thenReply(cu.replyTo)(_ => StatusReply.error("email already in use"))
          case None => Effect.persist(cu.asEvent).thenReply(cu.replyTo)(_ => StatusReply.success(cu.asEvent.asEntity.asResponse))
        }
      case uu: Command.UpdateUser =>
        state.find(uu.id) match {
          case Some(_) => Effect.persist(uu.asEvent).thenReply(uu.replyTo)(updatedState =>
            StatusReply.success(updatedState.find(uu.id).get.asResponse)
          )
          case None => Effect.none.thenReply(uu.replyTo)(_ => StatusReply.error(s"user with id ${uu.id} not found"))
        }
      case Command.DeleteUser(email, replyTo) =>
        state.find(email) match {
          case Some(user) => Effect.persist(Event.UserDeleted(user.id)).thenReply(replyTo)(_ => StatusReply.success(user.asResponse))
          case None => Effect.none.thenReply(replyTo)(_ => StatusReply.error(s"user with email $email not found"))
        }

      case Command.Login(email, passwd, replyTo) => state.find(email) match {
        case Some(user) if user.password == passwd =>
          Effect.persist(Event.LoggedIn(user.id)).thenReply(replyTo) { state =>
            state.authorize(user.id) match {
              case Some(au) => StatusReply.success(au.asOAuthToken)
              case None => StatusReply.error(s"!!!: user[${user.email}] is authenticated but no session found")
            }
          }
        case _ => Effect.none.thenReply(replyTo) { _ => StatusReply.error("invalid credentials") }
      }
      case Command.Authenticate(token, replyTo) => state.authorize(token) match {
        case Some(us) => Effect.persist(Event.Refreshed(us.id)).thenReply(replyTo)(_.authorize(token))
        case None => Effect.none.thenReply(replyTo)(_ => None)
      }
      case Command.Logout(token, replyTo) => state.authorize(token) match {
        case Some(us) => Effect.persist(Event.LoggedOut(us.id)).thenReply(replyTo)(_ => StatusReply.success("Yeah"))
        case None => Effect.none.thenReply(replyTo)(_ => StatusReply.error("User was not logged in"))
      }

      case Command.Reap(replyTo) =>
        val count = state.sessions.count(_.expires.isBefore(now))
        if (count == 0) {
          Effect.none.thenReply(replyTo)(_ => Done)
        } else {
          Effect.persist(Event.Reaped(ULID.newULID, count)).thenReply(replyTo)(_ => Done)
        }
      case Command.Info(replyTo) =>
        Effect.none.thenReply(replyTo)(state =>
          StatusReply.success(Response.Info(state.users.size, state.sessions.size, state.entries.size, recovered))
        )

      case ce: Command.CreateEntry => Effect.persist(ce.asEvent).thenReply(ce.replyTo)(_ => StatusReply.success(ce.asResponse))

      case cc: Command.CreateComment => Effect.persist(cc.asEvent).thenReply(cc.replyTo)(_ => StatusReply.success(cc.asResponse))
    }
  }

  private val eventHandler: (State, Event) => State = (state, event) => {
    Repository.eventHandler(event)
    event match {
      case uc: Event.UserCreated => state.save(uc.asEntity)
      case uu: Event.UserUpdated => state.find(uu.id).map(u => state.save(u.copy(name = uu.name, born = uu.born))).get
      case ud: Event.UserDeleted => state.delete(ud.id)

      case li: Event.LoggedIn => state.find(li.id).map(user => state.login(user, li.expires)).getOrElse(state)
      case lo: Event.LoggedOut => state.logout(lo.id)
      case re: Event.Refreshed => {
        state.logout(re.id)
        state.find(re.id).map(user => state.login(user, re.expires)).getOrElse(state)
      }

      case _: Event.Reaped => state.copy(sessions = state.sessions.filter(_.expires.isAfter(now)))

      case ec: Event.EntryCreated => state.save(ec.asEntity)
    }
  }
}
