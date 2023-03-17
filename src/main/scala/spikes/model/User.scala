package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import gremlin.scala.*
import io.scalaland.chimney.dsl.TransformerOps
import org.owasp.encoder.Encode
import spikes.validate.Validation.{FieldErrorInfo, validate}
import spikes.validate.{bornRule, emailRule, nameRule, passwordRule}
import wvlet.airframe.ulid.ULID

import java.time.{LocalDate, LocalDateTime, ZoneId}

case class User(
  id: ULID, name: String, email: String, password: String, born: LocalDate, @underlying vertex: Option[Vertex] = None
) extends Entity {
  val joined: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
  val tasks: Set[Task] = vertex.map(_.asScala()).map(_.out().hasLabel[Task]().toSet()).getOrElse(Set.empty).map(_.toCC[Task])
  val bookmarks: Set[Bookmark] = vertex.map(_.asScala()).map(_.out().hasLabel[Bookmark]().toSet()).getOrElse(Set.empty).map(_.toCC[Bookmark])
  def asResponse: User.Response = this.into[User.Response]
    .withFieldComputed(_.tasks, _ => tasks.map(_.asResponse))
    .withFieldComputed(_.bookmarks, _ => bookmarks.map(_.asResponse))
    .transform
  def asSession(expires: LocalDateTime): UserSession = UserSession(hash(ULID.newULIDString), id, expires)
}

object User {

  type ReplyTo = ActorRef[StatusReply[User.Response]]
  type ReplyListTo = ActorRef[StatusReply[List[User.Response]]]
  type ReplyTokenTo = ActorRef[StatusReply[OAuthToken]]
  type ReplySessionTo = ActorRef[Option[UserSession]]
  type ReplyAnyTo = ActorRef[StatusReply[Any]]

  case class Post(name: String, email: String, password: String, born: LocalDate) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set(
      validate(nameRule(name), name, "name"),
      validate(emailRule(email), email, "email"),
      validate(passwordRule(password), password, "password"),
      validate(bornRule(born), born, "born")
    ).flatten
    def asCmd(replyTo: ReplyTo): Create = Create(ULID.newULID, Encode.forHtml(name), email, hash(password), born, replyTo)
  }
  case class Put(id: ULID, name: String, password: String, born: LocalDate) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set(
      validate(nameRule(name), name, "name"),
      validate(passwordRule(password), password, "password"),
      validate(bornRule(born), born, "born")
    ).flatten
    def asCmd(replyTo: ReplyTo): Update = Update(id, name, hash(password), born, replyTo)
  }
  case class Delete(email: String) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set(validate(emailRule(email), email, "email")).flatten
    def asCmd(replyTo: ReplyTo): Remove = Remove(email, replyTo)
  }
  case class Authenticate(email: String, password: String) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set(
      validate(emailRule(email), email, "email"),
      validate(passwordRule(password), password, "password")
    ).flatten
    def asCmd(replyTo: ReplyTokenTo): Login = Login(email, hash(password), replyTo)
  }

  case class Create(id: ULID, name: String, email: String, password: String, born: LocalDate, replyTo: ReplyTo) extends Command {
    lazy val joined: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
    def asResponse: User.Response = this.into[User.Response]
      .withFieldComputed(_.tasks, _ => Set.empty[Task.Response])
      .withFieldComputed(_.bookmarks, _ => Set.empty[Bookmark.Response])
      .transform
    def asEvent: Created = this.into[Created].transform
  }
  case class Update(id: ULID, name: String, password: String, born: LocalDate, replyTo: ReplyTo) extends Command {
    def asEvent: Updated = this.into[Updated].transform
  }
  case class Remove(email: String, replyTo: ReplyTo) extends Command

  case class Find(id: ULID, replyTo: ReplyTo) extends Command
  case class All(replyTo: ReplyListTo) extends Command

  case class Login(email: String, password: String, replyTo: ReplyTokenTo) extends Command
  case class Authorize(token: String, replyTo: ReplySessionTo) extends Command
  case class Logout(token: String, replyTo: ReplyAnyTo) extends Command

  case class Created(id: ULID, name: String, email: String, password: String, born: LocalDate) extends Event {
    def asEntity: User = this.into[User].withFieldComputed(_.vertex, _ => None).transform
  }
  case class Updated(id: ULID, name: String, password: String, born: LocalDate) extends Event
  case class Removed(id: ULID) extends Event

  case class LoggedIn(id: ULID, expires: LocalDateTime = now.plusHours(2)) extends Event
  case class LoggedOut(id: ULID) extends Event

  case class Response(
    id: ULID, name: String, email: String, joined: LocalDateTime, born: LocalDate,
    tasks: Set[Task.Response],
    bookmarks: Set[Bookmark.Response]
  ) extends Respons
}
