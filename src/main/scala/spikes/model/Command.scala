package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.scalaland.chimney.dsl.TransformerOps

import java.time.LocalDate
import java.util.UUID

trait Command extends CborSerializable

object Command {
  case class CreateUser(
    id: UUID,
    name: String,
    email: String,
    born: LocalDate,
    password: String,
    replyTo: ActorRef[StatusReply[Response.User]]
  ) extends Command {
    lazy val asEvent: Event.UserCreated = this.into[Event.UserCreated].withFieldComputed(_.joined, _ => now()).transform
  }

  case class UpdateUser(
    id: UUID, name: String, born: LocalDate, password: String, replyTo: ActorRef[StatusReply[Response.User]]
  ) extends Command {
    lazy val asEvent: Event.UserUpdated = this.into[Event.UserUpdated].transform
  }

  case class DeleteUser(email: String, replyTo: ActorRef[StatusReply[Response.User]]) extends Command {
    lazy val asEvent: Event.UserDeleted = this.into[Event.UserDeleted].transform
  }

  case class Login(email: String, password: String, replyTo: ActorRef[StatusReply[OAuthToken]]) extends Command
  case class Authenticate(token: String, replyTo: ActorRef[Option[UserSession]]) extends Command

  case class FindUserById(id: UUID, replyTo: ActorRef[StatusReply[Response.User]]) extends Command
  case class FindUserByEmail(email: String, replyTo: ActorRef[StatusReply[Response.User]]) extends Command
  case class FindAllUser(replyTo: ActorRef[StatusReply[List[Response.User]]]) extends Command

  case class Reap(replyTo: ActorRef[Command]) extends Command
}
