package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import org.owasp.encoder.Encode
import spikes.model.Status._
import spikes.validate.{Rules, Validation}
import wvlet.airframe.ulid.ULID

import java.time.{LocalDate, LocalDateTime}

trait Request

object Request {
  final case class CreateUser(name: String, email: String, password: String, born: LocalDate) extends Request {
    val rules = Rules.createUser
    lazy val valid: Boolean = Validation.validate(this, rules).isEmpty
    def asCmd(replyTo: ActorRef[StatusReply[Response.User]]): Command.CreateUser = Command.CreateUser(
      ULID.newULID, name, email, born, hash(password), replyTo
    )
  }

  final case class UpdateUser(id: ULID, name: String, password: String, born: LocalDate) extends Request {
    val rules = Rules.updateUser
    def asCmd(replyTo: ActorRef[StatusReply[Response.User]]): Command.UpdateUser = Command.UpdateUser(
      id, name, born, hash(password), replyTo
    )
  }

  final case class DeleteUser(email: String) extends Request {
    val rules = Rules.deleteUser
    def asCmd(replyTo: ActorRef[StatusReply[Response.User]]): Command.DeleteUser = Command.DeleteUser(email, replyTo)
  }

  final case class Login(email: String, password: String) extends Request {
    val rules = Rules.login
    def asCmd(replyTo: ActorRef[StatusReply[OAuthToken]]): Command.Login = Command.Login(email, hash(password), replyTo)
  }


  final case class CreateEntry(
    title: String,
    body: String,
    status: Status = Status.Blank,
    url: Option[String] = None,
    due: Option[LocalDateTime] = None,
    starts: Option[LocalDateTime] = None,
    ends: Option[LocalDateTime] = None
  ) extends Request {
    def asCmd(user: ULID, replyTo: ActorRef[StatusReply[Response.Entry]]): Command.CreateEntry = Command.CreateEntry(
      ULID.newULID, user, Encode.forHtml(title), Encode.forHtml(body), replyTo, status
    )
  }

  final case class CreateComment(title: String, body: String) extends Request {
    def asCmd(user: ULID, entry: ULID, replyTo: ActorRef[StatusReply[Response.Comment]]): Command.CreateComment = Command.CreateComment(
      ULID.newULID, entry, user, Encode.forHtml(title), Encode.forHtml(body), replyTo
    )
  }
}
