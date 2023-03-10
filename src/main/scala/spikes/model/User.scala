package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.scalaland.chimney.dsl.TransformerOps
import wvlet.airframe.ulid.ULID

import java.time.{LocalDate, LocalDateTime, ZoneId}

case class User(
  id: ULID, name: String, email: String, password: String, born: LocalDate,
) extends Entity {
  lazy val joined: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
  def asResponse: User.Response = this.into[User.Response].withFieldComputed(_.tasks, _ => Seq.empty).transform
  def asSession(expires: LocalDateTime): UserSession = UserSession(hash(ULID.newULIDString), id, expires)
}

object User {

  type ReplyTo = ActorRef[StatusReply[User.Response]]
  type ReplyListTo = ActorRef[StatusReply[List[User.Response]]]
  type ReplyTokenTo = ActorRef[StatusReply[OAuthToken]]
  type ReplySessionTo = ActorRef[Option[UserSession]]
  type ReplyAnyTo = ActorRef[StatusReply[Any]]

  case class Post(name: String, email: String, password: String, born: LocalDate) extends Request {
    def asCmd(replyTo: ReplyTo): Create = Create(ULID.newULID, name, email, hash(password), born, replyTo)
  }
  case class Put(id: ULID, name: String, password: String, born: LocalDate) extends Request {
    def asCmd(replyTo: ReplyTo): Update = Update(id, name, hash(password), born, replyTo)
  }
  case class Delete(email: String) extends Request {
    def asCmd(replyTo: ReplyTo): Remove = Remove(email, replyTo)
  }

  case class ReqLogin(email: String, password: String) extends Request {
    def asCmd(replyTo: ReplyTokenTo): Login = Login(email, hash(password), replyTo)
  }

  case class Create(id: ULID, name: String, email: String, password: String, born: LocalDate, replyTo: ReplyTo) extends Command {
    lazy val joined: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
    def asResponse: User.Response = this.into[User.Response].withFieldComputed(_.tasks, _ => Seq.empty).transform
    def asEvent: Created = this.into[Created].transform
  }
  case class Update(id: ULID, name: String, password: String, born: LocalDate, replyTo: ReplyTo) extends Command {
    def asEvent: Updated = this.into[Updated].transform
  }
  case class Remove(email: String, replyTo: ReplyTo) extends Command

  case class Find(id: ULID, replyTo: ReplyTo) extends Command
  case class All(replyTo: ReplyListTo) extends Command

  case class Login(email: String, password: String, replyTo: ReplyTokenTo) extends Command
  case class Authenticate(token: String, replyTo: ReplySessionTo) extends Command
  case class Logout(token: String, replyTo: ReplyAnyTo) extends Command

  case class Created(id: ULID, name: String, email: String, password: String, born: LocalDate) extends Event {
    def asEntity: User = this.into[User].transform
  }
  case class Updated(id: ULID, name: String, password: String, born: LocalDate) extends Event
  case class Removed(id: ULID) extends Event

  case class LoggedIn(id: ULID, expires: LocalDateTime = now.plusHours(2)) extends Event
  case class Refreshed(id: ULID, expires: LocalDateTime = now.plusHours(2)) extends Event
  case class LoggedOut(id: ULID) extends Event

  case class Response(id: ULID, name: String, email: String, joined: LocalDateTime, born: LocalDate, tasks: Seq[Task.Response]) extends Respons
}
