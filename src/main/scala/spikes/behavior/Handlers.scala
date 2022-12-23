package spikes.behavior

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PreRestart, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import akka.persistence.typed.{RecoveryCompleted, RecoveryFailed}
import spikes.Main.persistenceId
import spikes.model._

import java.time.LocalDateTime
import scala.concurrent.duration._

object Handlers {

  def apply(state: State = State(Users())): Behavior[Command] = Behaviors.setup { ctx =>
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      persistenceId = persistenceId,
      emptyState = state,
      commandHandler = commandHandler,
      eventHandler = eventHandler
    ).withEventPublishing(true)
      .withTagger(_ => Set("user"))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      .receiveSignal {
        case (_, PreRestart) => ctx.log.info("pre restart signal received")
        case (state, RecoveryCompleted) => ctx.log.info(s"recovery completed: ${state.users.size} user(s)")
        case (_, RecoveryFailed(t)) => ctx.log.error("recovery failed", t)
      }
  }

  private val commandHandler: (State, Command) => ReplyEffect[Event, State] = { (state, cmd) =>
    cmd match {
      case cu: Command.CreateUser =>
        state.find(cu.email) match {
          case Some(_) => Effect.none.thenReply(cu.replyTo) { _ => StatusReply.error("email already in use") }
          case None => Effect.persist(cu.asEvent).thenReply(cu.replyTo) { _ => StatusReply.success(cu.asEvent.asEntity.asResponse) }
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
          case Some(user) => Effect.persist(Event.UserDeleted(email)).thenReply(replyTo)(_ => StatusReply.success(user.asResponse))
          case None => Effect.none.thenReply(replyTo)(_ => StatusReply.error(s"user with email $email not found"))
        }
      case Command.Login(email, passwd, replyTo) =>
        state.find(email) match {
          case Some(user) if user.password == passwd =>
            Effect.persist(Event.LoggedIn(email)).thenReply(replyTo) { state =>
              state.authorize(user.id) match {
                case Some(au) => StatusReply.success(au.asOAuthToken)
                case None => StatusReply.error(s"!!: user[${user.email}] is authenticated but no session found")
              }
            }
          case Some(_) => Effect.none.thenReply(replyTo) { _ => StatusReply.error("invalid credentials") }
          case None => Effect.none.thenReply(replyTo) { _ => StatusReply.error(s"user with email $email not found") }
        }
      case Command.Authenticate(token, replyTo) => Effect.none.thenReply(replyTo) { state => state.authorize(token) }
      case Command.Reap(replyTo) =>
        val count = state.sessions.count(_.expires.isBefore(now()))
        if (count == 0) {
          Effect.none.thenReply(replyTo)(_ => Done)
        } else {
          Effect.persist(Event.Reaped(count)).thenReply(replyTo)(_ => Done)
        }
      case Command.FindUserById(id, replyTo) =>
        Effect.none.thenReply(replyTo)(_.find(id)
          .map(u => StatusReply.success(u.asResponse)).getOrElse(StatusReply.error(s"user $id not found")))
      case Command.FindUserByEmail(email, replyTo) =>
        Effect.none.thenReply(replyTo)(_.find(email)
          .map(u => StatusReply.success(u.asResponse)).getOrElse(StatusReply.error(s"user ${email} not found")))
      case Command.FindAllUser(replyTo) =>
        Effect.none.thenReply(replyTo)(state => StatusReply.success(state.users.ids.values.toList.map(_.asResponse)))
    }
  }

  private val eventHandler: (State, Event) => State = { (state, event) =>
    event match {
      case uc: Event.UserCreated => state.save(uc.asEntity)
      case uu: Event.UserUpdated => state.find(uu.id).map(u => state.save(u.copy(name = uu.name, born = uu.born))).get
      case ud: Event.UserDeleted => state.delete(ud.email)
      case li: Event.LoggedIn => state.find(li.email).map(user => state.authenticate(user, li.expires)).getOrElse(state)
      case _ : Event.Reaped => state.copy(sessions = state.sessions.filter(_.expires.isAfter(LocalDateTime.now())))
    }
  }
}
