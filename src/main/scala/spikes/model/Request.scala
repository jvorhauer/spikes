package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.scalaland.chimney.dsl.TransformerOps
import spikes.validate.{Rules, Validation}
import wvlet.airframe.ulid.ULID

import java.time.LocalDate

trait Request

object Request {
  final case class CreateUser(name: String, email: String, password: String, born: LocalDate) extends Request {
    val rules = Rules.createUser
    lazy val valid: Boolean = Validation.validate(this, rules).isEmpty
    def asCmd(replyTo: ActorRef[StatusReply[Response.User]]): Command.CreateUser = this.into[Command.CreateUser]
      .withFieldComputed(_.id, _ => ULID.newULID)
      .withFieldComputed(_.password, req => hash(req.password))
      .withFieldComputed(_.replyTo, _ => replyTo)
      .transform
  }

  final case class UpdateUser(id: ULID, name: String, password: String, born: LocalDate) extends Request {
    val rules = Rules.updateUser
    lazy val valid: Boolean = Validation.validate(this, rules).isEmpty
    def asCmd(replyTo: ActorRef[StatusReply[Response.User]]): Command.UpdateUser = this.into[Command.UpdateUser]
      .withFieldComputed(_.password, req => hash(req.password))
      .withFieldComputed(_.replyTo, _ => replyTo)
      .transform
  }

  final case class DeleteUser(email: String) extends Request {
    val rules = Rules.deleteUser
    lazy val valid: Boolean = Validation.validate(this, rules).isEmpty
    def asCmd(replyTo: ActorRef[StatusReply[Response.User]]): Command.DeleteUser = this.into[Command.DeleteUser]
      .withFieldComputed(_.replyTo, _ => replyTo)
      .transform
  }

  final case class Login(email: String, password: String) extends Request {
    val rules = Rules.login
    lazy val valid: Boolean = Validation.validate(this, rules).isEmpty
    def asCmd(replyTo: ActorRef[StatusReply[OAuthToken]]): Command.Login = Command.Login(email, hash(password), replyTo)
  }


  final case class CreateEntry(user: ULID, title: String, body: String) extends Request {

  }
}
