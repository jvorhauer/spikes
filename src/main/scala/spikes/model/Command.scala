package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.scalaland.chimney.dsl.TransformerOps
import wvlet.airframe.ulid.ULID
import spikes.model.Status._

import java.time.{LocalDate, LocalDateTime, ZoneId}

trait Command extends CborSerializable

object Command {

  type ReplyUserTo = ActorRef[StatusReply[Response.User]]
  type ReplyTokenTo = ActorRef[StatusReply[OAuthToken]]
  type ReplySessionTo = ActorRef[Option[UserSession]]
  type ReplyAnyTo = ActorRef[StatusReply[_]]
  type ReplyUserListTo = ActorRef[StatusReply[List[Response.User]]]
  type ReplyInfoTo = ActorRef[StatusReply[Response.Info]]
  type ReplyEntryTo = ActorRef[StatusReply[Response.Entry]]

  case class CreateUser(id: ULID, name: String, email: String, born: LocalDate, password: String, replyTo: ReplyUserTo) extends Command {
    lazy val asEvent: Event.UserCreated = this.into[Event.UserCreated].transform
  }

  case class UpdateUser(id: ULID, name: String, born: LocalDate, password: String, replyTo: ReplyUserTo) extends Command {
    lazy val asEvent: Event.UserUpdated = this.into[Event.UserUpdated].transform
  }

  case class DeleteUser(email: String, replyTo: ReplyUserTo) extends Command

  case class Login(email: String, password: String, replyTo: ReplyTokenTo) extends Command
  case class Authenticate(token: String, replyTo: ReplySessionTo) extends Command
  case class Logout(token: String, replyTo: ReplyAnyTo) extends Command

  case class FindUserById(id: ULID, replyTo: ReplyUserTo) extends Command
  case class FindUserByEmail(email: String, replyTo: ReplyUserTo) extends Command
  case class FindAllUser(replyTo: ReplyUserListTo) extends Command

  case class Reap(replyTo: ActorRef[Command]) extends Command
  case class Info(replyTo: ReplyInfoTo) extends Command

  case class CreateEntry(
    id: ULID,
    owner: ULID,
    title: String,
    body: String,
    replyTo: ReplyEntryTo,
    status: Status = Status.Blank,
    url: Option[String] = None,
    due: Option[LocalDateTime] = None,
    starts: Option[LocalDateTime] = None,
    ends: Option[LocalDateTime] = None
  ) extends Command {
    lazy val asEvent: Event.EntryCreated = this.into[Event.EntryCreated].transform
    lazy val asResponse: Response.Entry = this.into[Response.Entry].transform
    lazy val written: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
  }
}
