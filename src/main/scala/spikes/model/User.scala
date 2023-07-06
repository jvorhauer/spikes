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

@label("user")
case class User(
  id: ULID,
  name: String, email: String, password: String,
  born: LocalDate,
  bio: Option[String] = None,
  @underlying vertex: Option[Vertex] = None
) extends Entity {
  val joined: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
  private def vrtx = vertex.map(_.asScala())
  def tasks: Set[Task] = vrtx.map(_.out().hasLabel[Task]().toSet()).getOrElse(Set.empty).map(_.toCC[Task])
  def following: Set[User] = vrtx.map(_.out().hasLabel[User]().toSet()).getOrElse(Set.empty).map(_.toCC[User])
  def followedBy: Set[User] = vrtx.map(_.in().hasLabel[User]().toSet()).getOrElse(Set.empty).map(_.toCC[User])
  def asResponse: User.Response = User.Response(
    id, name, email, joined, born, bio.getOrElse(""),
    tasks.map(_.asResponse),
    following.map(_.id),
    followedBy.map(_.id)
  )
  def asSession(expires: LocalDateTime): User.Session = User.Session(hash(ULID.newULIDString), id, expires)
}

object User {

  type ReplyTo = ActorRef[StatusReply[User.Response]]
  type ReplyListTo = ActorRef[StatusReply[List[User.Response]]]
  type ReplyTokenTo = ActorRef[StatusReply[OAuthToken]]
  type ReplySessionTo = ActorRef[Option[User.Session]]
  type ReplyAnyTo = ActorRef[StatusReply[Any]]

  case class Post(name: String, email: String, password: String, born: LocalDate, bio: Option[String] = None) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set(
      validate(nameRule(name), name, "name"),
      validate(emailRule(email), email, "email"),
      validate(passwordRule(password), password, "password"),
      validate(bornRule(born), born, "born")
    ).flatten
    def asCmd(replyTo: ReplyTo): Create = Create(ULID.newULID, Encode.forHtml(name), email, hash(password), born, bio, replyTo)
  }
  case class Put(id: ULID, name: String, password: String, born: LocalDate, bio: Option[String] = None) extends Request {
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
  case class RequestFollow(id: ULID, other: ULID) extends Request {
    lazy val validated: Set[FieldErrorInfo] = Set()
    def asCmd(replyTo: ReplyAnyTo): Follow = Follow(id, other, replyTo)
  }

  case class Create(id: ULID, name: String, email: String, password: String, born: LocalDate, bio: Option[String], replyTo: ReplyTo) extends Command {
    lazy val joined: LocalDateTime = LocalDateTime.ofInstant(id.toInstant, ZoneId.of("UTC"))
    def asResponse: Response = Response(
      id, name, email, joined, born, bio.getOrElse(""),
      Set[Task.Response](), Set[ULID](), Set[ULID]()
    )
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

  case class Follow(id: ULID, other: ULID, replyTo: ReplyAnyTo) extends Command

  case class Created(id: ULID, name: String, email: String, password: String, born: LocalDate, bio: Option[String]) extends Event {
    def asEntity: User = User(id, name, email, password, born, bio)
  }
  case class Updated(id: ULID, name: String, password: String, born: LocalDate) extends Event
  case class Removed(id: ULID) extends Event

  case class LoggedIn(id: ULID, expires: LocalDateTime = now.plusHours(2)) extends Event
  case class LoggedOut(id: ULID) extends Event
  case class Followed(id: ULID, other: ULID) extends Event

  case class Response(
    id: ULID, name: String, email: String, joined: LocalDateTime, born: LocalDate, bio: String,
    tasks: Set[Task.Response],
    following: Set[ULID],
    followedBy: Set[ULID]
  ) extends Respons

  case class Session(token: String, id: ULID, expires: LocalDateTime = now.plusHours(2)) {
    lazy val asOAuthToken: OAuthToken = OAuthToken(token)
  }
}
