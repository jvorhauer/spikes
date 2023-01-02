package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.scalaland.chimney.dsl.TransformerOps
import wvlet.airframe.ulid.ULID

import java.time.LocalDate

trait Command extends CborSerializable

object Command {
  case class CreateUser(
    id: ULID,
    name: String,
    email: String,
    born: LocalDate,
    password: String,
    replyTo: ActorRef[StatusReply[Response.User]]
  ) extends Command {
    lazy val asEvent: Event.UserCreated = this.into[Event.UserCreated].transform
  }

  case class UpdateUser(
    id: ULID, name: String, born: LocalDate, password: String, replyTo: ActorRef[StatusReply[Response.User]]
  ) extends Command {
    lazy val asEvent: Event.UserUpdated = this.into[Event.UserUpdated].transform
  }

  case class DeleteUser(email: String, replyTo: ActorRef[StatusReply[Response.User]]) extends Command

  case class Login(email: String, password: String, replyTo: ActorRef[StatusReply[OAuthToken]]) extends Command
  case class Authenticate(token: String, replyTo: ActorRef[Option[UserSession]]) extends Command
  case class Logout(token: String, replyTo: ActorRef[StatusReply[_]]) extends Command

  case class FindUserById(id: ULID, replyTo: ActorRef[StatusReply[Response.User]]) extends Command
  case class FindUserByEmail(email: String, replyTo: ActorRef[StatusReply[Response.User]]) extends Command
  case class FindAllUser(replyTo: ActorRef[StatusReply[List[Response.User]]]) extends Command

  case class Reap(replyTo: ActorRef[Command]) extends Command
  case class Info(replyTo: ActorRef[StatusReply[Response.Info]]) extends Command
}
