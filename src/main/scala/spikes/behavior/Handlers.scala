package spikes.behavior

import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PreRestart}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted, RecoveryFailed}
import spikes.model._
import spikes.{Command, Event}

import java.time.LocalDateTime
import scala.concurrent.duration._

object Handlers {

  def apply(state: State = State(Users())): Behavior[Command] = Behaviors.setup { ctx =>
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("spikes-handlers"),
      emptyState = state,
      commandHandler = commandHandler,
      eventHandler = eventHandler
    ).withEventPublishing(true)
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      .receiveSignal {
        case (_, PreRestart) => ctx.log.info("pre restart signal received")
        case (state, RecoveryCompleted) => ctx.log.info(s"recovery completed: ${state.users.size} user(s)")
        case (_, RecoveryFailed(t)) => ctx.log.error("recovery failed", t)
      }
  }

  private val commandHandler: (State, Command) => ReplyEffect[Event, State] = { (state, cmd) =>
    cmd match {
      case cu: CreateUser =>
        state.get(cu.email) match {
          case Some(_) => Effect.none.thenReply(cu.replyTo) { _ => StatusReply.error("email already in use") }
          case None => Effect.persist(cu.asEvent).thenReply(cu.replyTo) { _ => StatusReply.success(cu.asEvent.asEntity.asResponse) }
        }
      case uu: UpdateUser =>
        state.get(uu.id) match {
          case Some(_) => Effect.persist(uu.asEvent).thenReply(uu.replyTo)(updatedState =>
            StatusReply.success(updatedState.get(uu.id).get.asResponse)
          )
          case None => Effect.none.thenReply(uu.replyTo)(_ => StatusReply.error(s"user with id ${uu.id} not found"))
        }
      case DeleteUser(email, replyTo) =>
        state.get(email) match {
          case Some(user) => Effect.persist(UserDeleted(email)).thenReply(replyTo)(_ => StatusReply.success(user.asResponse))
          case None => Effect.none.thenReply(replyTo)(_ => StatusReply.error(s"user with email $email not found"))
        }
      case Login(email, passwd, replyTo) =>
        state.get(email) match {
          case Some(user) if user.password == passwd =>
            Effect.persist(LoggedIn(email)).thenReply(replyTo) { state =>
              state.authorize(user.id) match {
                case Some(au) => StatusReply.success(au.asOAuthToken)
                case None => StatusReply.error(s"!!: user[${user.email}] is authenticated but no session found")
              }
            }
          case Some(_) =>
            Effect.none.thenReply(replyTo) { _ => StatusReply.error("invalid credentials") }
          case None => Effect.none.thenReply(replyTo) { _ => StatusReply.error(s"user with email $email not found") }
        }
      case Reap(replyTo) =>
        val count = state.sessions.count(_.expires.isBefore(now))
        if (count == 0) {
          Effect.none.thenReply(replyTo)(_ => Done)
        } else {
          Effect.persist(Reaped(count)).thenReply(replyTo)(_ => Done)
        }
      case FindUserById(id, replyTo) =>
        Effect.none.thenReply(replyTo) { _ =>
          state.get(id) match {
            case Some(user) => StatusReply.success(user.asResponse)
            case None => StatusReply.error(s"user with id $id not found")
          }
        }
    }
  }

  private val eventHandler: (State, Event) => State = { (state, event) =>
    event match {
      case uc: UserCreated =>
        state.put(uc.asEntity)
      case uu: UserUpdated =>
        state.get(uu.id).map(u => state.put(u.copy(name = uu.name, email = uu.email, born = uu.born))).get
      case ud: UserDeleted => state.rem(ud.email)
      case LoggedIn(email, expires) => state.get(email).map(user => state.authenticate(user, expires)).getOrElse(state)
      case _: Reaped => state.copy(sessions = state.sessions.filter(_.expires.isAfter(LocalDateTime.now())))
    }
  }

  private def now = LocalDateTime.now()
}
