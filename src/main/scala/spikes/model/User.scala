package spikes.model

import io.scalaland.chimney.dsl.TransformerOps
import spikes.validate.{FieldRule, ModelValidation}
import spikes.{Command, Entity, Event, Hasher, Request, Response}

import java.time.LocalDateTime.now
import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

object Regexes {
  val name  = "^[a-zA-Z '-]+$"
  val email = "^([\\w-]+(?:\\.[\\w-]+)*)@\\w[\\w.-]+\\.[a-zA-Z]+$"
  val poco  = "^[1-9][0-9]{3} ?[a-zA-Z]{2}$"
  val passw = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#&()–[{}]:;',?/*~$^+=<>]).{8,42}$"
}

trait UserRequest extends Request {
  def name: String
  def email: String
  def password: String
  def born: LocalDate
  def isValid: Boolean
  def isInvalid: Boolean = !isValid
}

object UserRequest {
  val rules = Set(
    FieldRule("name", (name: String) => name.matches(Regexes.name), "invalid name"),
    FieldRule("email", (email: String) => email.matches(Regexes.email), "invalid email address"),
    FieldRule("password", (password: String) => password.matches(Regexes.passw), "invalid password"),
    FieldRule("born", (born: LocalDate) =>
      born.isBefore(LocalDate.now().minusYears(8)) && born.isAfter(LocalDate.now().minusYears(121)),
      "too old or young"
    )
  )
}

case class RequestCreateUser(name: String, email: String, password: String, born: LocalDate) extends UserRequest {
  lazy val isValid: Boolean = ModelValidation.validate(this, UserRequest.rules).isEmpty
  lazy val asCmd: CreateUser = this.into[CreateUser]
    .withFieldComputed(_.id, _ => UUID.randomUUID())
    .withFieldComputed(_.password, req => Hasher.hash(req.password))
    .transform
}

case class RequestUpdateUser(id: UUID, name: String, email: String, password: String, born: LocalDate) extends UserRequest {
  lazy val isValid: Boolean = ModelValidation.validate(this, RequestUpdateUser.rules).isEmpty
  lazy val asCmd: UpdateUser = this.into[UpdateUser].transform
}
object RequestUpdateUser {
  val rules = UserRequest.rules ++ Set(FieldRule("id", (id: UUID) => id != null, "no id specified"))
}

case class RequestDeleteUser(id: UUID) extends Request {
  lazy val asCmd: DeleteUser = this.into[DeleteUser].transform
}


case class CreateUser(id: UUID, name: String, email: String, born: LocalDate, password: String) extends Command {
  lazy val asEvent: UserCreated = this.into[UserCreated].withFieldComputed(_.joined, _ => now()).transform
}

case class UpdateUser(id: UUID, name: String, email: String, born: LocalDate, password: String) extends Command {
  lazy val asEvent: UserUpdated = this.into[UserUpdated].transform
}

case class DeleteUser(id: UUID) extends Command {
  lazy val asEvent: UserDeleted = this.into[UserDeleted].transform
}

case class UserCreated(
  id: UUID, name: String, email: String, password: String, joined: LocalDateTime, born: LocalDate
) extends Event {
  lazy val asEntity = this.into[User].withFieldComputed(_.entries, _ => Map[UUID, Entry]()).transform
}

case class UserUpdated(id: UUID, name: String, email: String, password: String, born: LocalDate) extends Event

case class UserDeleted(id: UUID) extends Event

case class User(
  id: UUID,
  name: String,
  email: String,
  password: String,
  joined: LocalDateTime,
  born: LocalDate,
  entries: Map[UUID, Entry]
) extends Entity {
  lazy val asResponse: UserResponse = this.into[UserResponse].transform
}

case class UserResponse(id: UUID, name: String, email: String, joined: LocalDateTime, born: LocalDate) extends Response
