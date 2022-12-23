package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.scalaland.chimney.dsl.TransformerOps
import spikes.validate.{FieldRule, ModelValidation, Regexes}

import java.time.LocalDate
import java.util.UUID

trait Request

object Request {
  case class CreateUser(name: String, email: String, password: String, born: LocalDate) extends Request {
    val rules = Rules.createUser
    lazy val valid: Boolean = ModelValidation.validate(this, rules).isEmpty
    def asCmd(replyTo: ActorRef[StatusReply[Response.User]]): Command.CreateUser = this.into[Command.CreateUser]
      .withFieldComputed(_.id, _ => UUID.randomUUID())
      .withFieldComputed(_.password, req => hash(req.password))
      .withFieldComputed(_.replyTo, _ => replyTo)
      .transform
  }

  case class UpdateUser(id: UUID, name: String, password: String, born: LocalDate) extends Request {
    val rules = Rules.updateUser
    lazy val valid: Boolean = ModelValidation.validate(this, rules).isEmpty
    def asCmd(replyTo: ActorRef[StatusReply[Response.User]]): Command.UpdateUser = this.into[Command.UpdateUser]
      .withFieldComputed(_.password, req => hash(req.password))
      .withFieldComputed(_.replyTo, _ => replyTo)
      .transform
  }

  case class DeleteUser(email: String) extends Request {
    val rules = Rules.deleteUser
    lazy val valid: Boolean = ModelValidation.validate(this, rules).isEmpty
    def asCmd(replyTo: ActorRef[StatusReply[Response.User]]): Command.DeleteUser = this.into[Command.DeleteUser]
      .withFieldComputed(_.replyTo, _ => replyTo)
      .transform
  }

  case class Login(email: String, password: String) extends Request {
    val rules = Rules.login
    lazy val valid: Boolean = ModelValidation.validate(this, rules).isEmpty
    def asCmd(replyTo: ActorRef[StatusReply[OAuthToken]]): Command.Login = Command.Login(email, hash(password), replyTo)
  }
}

object Rules {

  private val nameFieldRule = FieldRule("name", (name: String) => name.matches(Regexes.name), "invalid name")
  private val emailFieldRule = FieldRule("email", (email: String) => email.matches(Regexes.email), "invalid email address")
  private val passwordFieldRule = FieldRule("password", (password: String) => password.matches(Regexes.passw), "invalid password")
  private val bornFieldRules = Set(
    FieldRule("born", (born: LocalDate) => born.isBefore(LocalDate.now().minusYears(8)), "too young"),
    FieldRule("born", (born: LocalDate) => born.isAfter(LocalDate.now().minusYears(121)), "too old")
  )
  private val idFieldRule = FieldRule("id", (id: UUID) => id != null, "no id specified")

  val createUser = Set(nameFieldRule, emailFieldRule, passwordFieldRule) ++ bornFieldRules
  val updateUser = Set(nameFieldRule, passwordFieldRule, idFieldRule) ++ bornFieldRules
  val deleteUser = Set(emailFieldRule)
  val login = Set(emailFieldRule, passwordFieldRule)
}
