package spikes.behavior

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PublishedEvent
import spikes.model._

import scala.collection.mutable

sealed trait Query

case class AllUsers(replyTo: ActorRef[List[Response.User]]) extends Query

object Reader {

  private val users: mutable.Set[User] = mutable.HashSet.empty[User]
  private var loggedIn: Int = 0

  def apply(): Behavior[PublishedEvent] =
    Behaviors.receive {
      case (ctx, pe) =>
        pe.event match {
          case li: Event.LoggedIn =>
            ctx.log.info("reader: logged in: {}", li)
            loggedIn += 1
          case uc: Event.UserCreated =>
            ctx.log.info("reader: created user: {}", uc.email)
            users += uc.asEntity
          case ud: Event.UserDeleted =>
            ctx.log.info("reader: deleted user: {}", ud.email)
          case r: Event.Reaped =>
            ctx.log.info("reader: reaped {} sessions", r.eligible)
            loggedIn -= r.eligible
          case _ => ctx.log.info("reader: match _")
        }
        Behaviors.same
    }

  def query(): Behavior[Query] =
    Behaviors.receive {
      case (ctx, q) =>
        q match {
          case AllUsers(replyTo) => replyTo.tell(users.map(u => u.asResponse).toList)
          case _ => ctx.log.warn("query: not implemented yet")
        }
        ctx.log.info("query: received {}", q)
        Behaviors.same
    }
}
