package spikes.behavior

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PreRestart, SupervisorStrategy}
import akka.pattern.StatusReply.{error, success}
import akka.persistence.typed.scaladsl.Effect.{persist, reply}
import akka.persistence.typed.scaladsl.{EventSourcedBehavior, Recovery, ReplyEffect, RetentionCriteria}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted, RecoveryFailed, SnapshotSelectionCriteria}
import gremlin.scala.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.scalactic.TripleEquals.*
import spikes.model.*
import spikes.route.InfoRouter.{GetInfo, Info}
import wvlet.airframe.ulid.ULID

import scala.concurrent.duration.DurationInt


object Handlers {

  val pid: PersistenceId = PersistenceId.of("spikes", "7", "|")

  implicit private val graph: ScalaGraph = TinkerGraph.open().asScala()

  def apply(state: State = State()): Behavior[Command] = Behaviors.setup { ctx =>
    var recovered = false

    val commandHandler: (State, Command) => ReplyEffect[Event, State] = (state, cmd) =>
      cmd match {
        case cu: User.Create =>
          state.findUser(cu.email) match {
            case Some(user) => reply(cu.replyTo)(error(s"email ${user.email} already in use"))
            case None       => persist(cu.asEvent).thenReply(cu.replyTo)(_ => success(cu.asResponse))
          }
        case uu: User.Update =>
          state.findUser(uu.id) match {
            case Some(user) => persist(uu.asEvent).thenReply(uu.replyTo)(us => success(us.findUser(user.id).get.asResponse))
            case None       => reply(uu.replyTo)(error(s"user with id ${uu.id} not found for update"))
          }
        case User.Remove(email, replyTo) =>
          state.findUser(email) match {
            case Some(user) => persist(User.Removed(user.id)).thenReply(replyTo)(_ => success(user.asResponse))
            case None       => reply(replyTo)(error(s"user with email $email not found for deletion"))
          }

        case User.Login(email, passwd, replyTo) => state.findUser(email) match {
          case Some(user) if user.password === passwd =>
            persist(User.LoggedIn(user.id)).thenReply(replyTo) { state =>
              state.authorize(user.id) match {
                case Some(au) => success(au.asOAuthToken)
                case None     => error(s"!!!: user[${user.email}] is authenticated but no session found")
              }
            }
          case _ => reply(replyTo)(error("invalid credentials"))
        }
        case User.Authorize(token, replyTo) => reply(replyTo)(state.authorize(token))
        case User.Logout(token, replyTo) => state.authorize(token) match {
          case Some(us) => persist(User.LoggedOut(us.id)).thenReply(replyTo)(_ => success("Yeah"))
          case None => reply(replyTo)(error("User was not logged in"))
        }

        case User.Follow(id, other, replyTo) => state.findUser(id) match {
          case Some(user) => state.findUser(other) match {
            case Some(otherUser) => persist(User.Followed(user.id, otherUser.id)).thenReply(replyTo)(_ => success("Followed"))
            case None => reply(replyTo)(error(s"can't follow user with id $other as that user can't be found"))
          }
          case None => reply(replyTo)(error(s"no user found for $id"))
        }

        case User.Find(id, replyTo) => state.getUserResponse(id) match {
          case Some(user) => reply(replyTo)(success(user))
          case None => reply(replyTo)(error(s"User $id not found"))
        }
        case User.All(replyTo) => reply(replyTo)(success(state.findUsers().map(_.asResponse)))

        case tc: Note.Create => state.findUser(tc.owner) match {
          case Some(_) => persist(tc.asEvent).thenReply(tc.replyTo)(_ => success(tc.asResponse))
          case None => reply(tc.replyTo)(error(s"Owner ${tc.owner} for new Note not found"))
        }
        case tu: Note.Update => state.findNote(tu.id) match {
          case Some(_) => persist(tu.asEvent).thenReply(tu.replyTo)(us => success(us.findNote(tu.id).get.asResponse))
          case None => reply(tu.replyTo)(error(s"Note ${tu.id} not found for update"))
        }
        case Note.Remove(id, replyTo) => state.findNote(id) match {
          case Some(t) => persist(Note.Removed(id)).thenReply(replyTo)(_ => success(t.asResponse))
          case None => reply(replyTo)(error(s"Note $id not found for deletion"))
        }
        case Note.Find(id, replyTo) => state.findNote(id) match {
          case Some(t) => reply(replyTo)(success(t.asResponse))
          case None => reply(replyTo)(error(s"Note $id not found"))
        }

        case Reaper.Reap(replyTo) =>
          state.sessions.count(_.expires.isBefore(now)) match {
            case 0 => reply(replyTo)(Reaper.Done)
            case count => persist(Reaper.Reaped(ULID.newULID, count)).thenReply(replyTo)(_ => Reaper.Done)
          }

        case GetInfo(replyTo) => reply(replyTo)(success(Info(state.userCount, state.sessions.size, state.noteCount, recovered)))
      }

    val eventHandler: (State, Event) => State = (state, evt) =>
      evt match {
        case uc: User.Created => state.save(uc.asEntity)
        case uu: User.Updated => state.findUser(uu.id).map(u => state.save(u.copy(name = uu.name, password = uu.password, born = uu.born))).getOrElse(state)
        case ud: User.Removed => state.deleteUser(ud.id)

        case li: User.LoggedIn if li.expires.isAfter(now) => state.findUser(li.id).map(state.login(_, li.expires)).getOrElse(state)
        case lo: User.LoggedOut => state.logout(lo.id)

        case uf: User.Followed => state.follow(uf.id, uf.other)

        case tc: Note.Created => state.save(tc.asNote)
        case tu: Note.Updated => state.save(tu.asNote)
        case tr: Note.Removed => state.deleteNote(tr.id)

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
          ctx.log.info(s"recovered: users: ${state.userCount}, sessions: ${state.sessions.size}, tasks: ${state.noteCount}")
          recovered = true
        case (_, RecoveryFailed(t)) => ctx.log.error("recovery failed", t)
      }
  }
}
