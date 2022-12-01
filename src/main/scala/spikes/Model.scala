package inherit

import com.wix.accord.dsl._
import com.wix.accord.{Success, Validator, validate}
import io.scalaland.chimney.dsl.TransformerOps

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.{LocalDate, LocalDateTime}
import java.time.LocalDateTime.now
import java.util.{Locale, UUID}
import java.util.regex.Pattern


trait Request {
  val asCmd: Command
}
trait Command {
  val asEvent: Event
}
trait Event
trait Entity { val id: UUID }
trait Response

object Hasher {
  private val md = MessageDigest.getInstance("SHA-256")
  private def toHex(ba: Array[Byte]): String = ba.map(s => String.format(Locale.US, "%02x", s)).mkString("")
  def hash(s: String): String = toHex(md.digest(s.getBytes(StandardCharsets.UTF_8)))
}

object Regexes {
  val name: Pattern = "^[a-zA-Z ']+$".r.pattern
  val email: Pattern = "^[a-zA-Z0-9._-]+@[a-zA-Z0-9._-]+\\.[a-zA-Z]+$".r.pattern
  val poco: Pattern = "^[0-9]{4} ?[A-Z]{2}$".r.pattern
  val passw: Pattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#&()–[{}]:;',?/*~$^+=<>]).{8,42}$".r.pattern
}

trait UserRequest extends Request {
  def name: String
  def email: String
  def password: String
  def born: LocalDate
  def isValid: Boolean
}
object UserRequest {
  val validated: Validator[UserRequest] = validator[UserRequest] { req =>
    req.name should matchRegex(Regexes.name)
    req.email should matchRegex(Regexes.email)
    req.password should matchRegex(Regexes.passw)
    req.born should be <= LocalDate.now().minusYears(8)
    req.born should be > LocalDate.now().minusYears(121)
  }
}

case class RequestCreateUser(name: String, email: String, password: String, born: LocalDate) extends UserRequest {
  lazy val isValid: Boolean = validate(this).isSuccess
  lazy val asCmd: CreateUser = this.into[CreateUser]
    .withFieldComputed(_.id, _ => UUID.randomUUID())
    .withFieldComputed(_.password, req => Hasher.hash(req.password))
    .transform
}
object RequestCreateUser {
  implicit val validated: Validator[RequestCreateUser] = validator[RequestCreateUser] { req =>
    req is valid(UserRequest.validated)
  }
}

case class RequestUpdateUser(id: UUID, name: String, email: String, password: String, born: LocalDate) extends UserRequest {
  lazy val isValid: Boolean = validate(this).isSuccess
  lazy val asCmd: UpdateUser = this.into[UpdateUser].transform
}
object RequestUpdateUser {
  implicit val validated: Validator[RequestUpdateUser] = validator[RequestUpdateUser] { req =>
    req is valid(UserRequest.validated)
    req.id is notNull
  }
}

case class RequestDeleteUser(id: UUID) extends Request {
  lazy val asCmd: DeleteUser = this.into[DeleteUser].transform
}


case class CreateUser(id: UUID, name: String, email: String, password: String) extends Command {
  lazy val asEvent: UserCreated = this.into[UserCreated].withFieldComputed(_.joined, _ => now()).transform
}
case class UpdateUser(id: UUID, name: String, email: String) extends Command {
  lazy val asEvent: UserUpdated = this.into[UserUpdated].transform
}
case class DeleteUser(id: UUID) extends Command {
  lazy val asEvent: UserDeleted = this.into[UserDeleted].transform
}

case class UserCreated(id: UUID, name: String, email: String, joined: LocalDateTime, born: LocalDate) extends Event
case class UserUpdated(id: UUID, name: String, email: String, born: LocalDate) extends Event
case class UserDeleted(id: UUID) extends Event

case class User(id: UUID, name: String, email: String, password: String, joined: LocalDateTime, born: LocalDate) extends Entity {
  def asResponse() = this.into[UserResponse].transform
}

case class UserResponse(id: String, name: String, email: String, joined: String, born: LocalDate) extends Response


case class Note(id: UUID, owner: User, title: String, body: String) extends Entity
case class Bookmark(id: UUID, marker: User, location: String, title: String, description: String) extends Entity
case class Task(id: UUID, reporter: User, title: String, description: String, due: LocalDateTime) extends Entity
case class Blog(id: UUID, owner: User, title: String, body: String, embargo: LocalDateTime) extends Entity
