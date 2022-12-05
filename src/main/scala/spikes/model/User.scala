package spikes.model

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.scalaland.chimney.dsl.TransformerOps
import spikes.validate.ModelValidation.validated
import spikes.validate.{FieldRule, ModelValidation}
import spikes.{Command, Entity, Event, Hasher, Request, Response}

import java.time.LocalDateTime.now
import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object Regexes {
  val name = "^[a-zA-Z '-]+$"
  val email = "^([\\w-]+(?:\\.[\\w-]+)*)@\\w[\\w.-]+\\.[a-zA-Z]+$"
  val poco = "^[1-9][0-9]{3} ?[a-zA-Z]{2}$"
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
  def asCmd(replyTo: ActorRef[StatusReply[UserResponse]]): CreateUser = this.into[CreateUser]
    .withFieldComputed(_.id, _ => UUID.randomUUID())
    .withFieldComputed(_.password, req => Hasher.hash(req.password))
    .withFieldComputed(_.replyTo, _ => replyTo)
    .transform
}

case class UserInputError(message: String)

case class RequestUpdateUser(id: UUID, name: String, email: String, password: String, born: LocalDate) extends UserRequest {
  lazy val isValid: Boolean = ModelValidation.validate(this, RequestUpdateUser.rules).isEmpty
  lazy val asCmd: UpdateUser = this.into[UpdateUser].transform
}

object RequestUpdateUser {
  val rules = UserRequest.rules ++ Set(FieldRule("id", (id: UUID) => id != null, "no id specified"))
}

case class RequestDeleteUser(email: String) extends Request {
  lazy val asCmd: DeleteUser = this.into[DeleteUser].transform
}


case class CreateUser(
  id: UUID,
  name: String,
  email: String,
  born: LocalDate,
  password: String,
  replyTo: ActorRef[StatusReply[UserResponse]]
) extends Command {
  lazy val asEvent: UserCreated = this.into[UserCreated].withFieldComputed(_.joined, _ => now()).transform
}

case class UpdateUser(id: UUID, name: String, email: String, born: LocalDate, password: String) extends Command {
  lazy val asEvent: UserUpdated = this.into[UserUpdated].transform
}

case class DeleteUser(email: String) extends Command {
  lazy val asEvent: UserDeleted = this.into[UserDeleted].transform
}

case class UserCreated(
  id: UUID, name: String, email: String, password: String, joined: LocalDateTime, born: LocalDate
) extends Event {
  lazy val asEntity = this.into[User].withFieldComputed(_.entries, _ => Map[UUID, Entry]()).transform
}

case class UserUpdated(id: UUID, name: String, email: String, password: String, born: LocalDate) extends Event

case class UserDeleted(email: String) extends Event

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

// IDEA: store User twice, once by email and once by id.toString. this way we can search for id or email
//       implication: do everything twice, think of something smart to make this less ugly...
case class State(users: Map[String, User] = Map.empty) {
  def has(email: String): Boolean = users.exists(_._1 == email)
  def get(email: String): Option[User] = users.get(email)
  def add(u: User): State = this.copy(users = users + (u.email -> u))
  def rem(email: String): State = this.copy(users = users.removed(email))
}


object UserBehavior {
  def apply(): Behavior[Command] = EventSourcedBehavior[Command, Event, State](
    persistenceId = PersistenceId.ofUniqueId("user"),
    emptyState = State(),
    commandHandler = commandHandler,
    eventHandler = eventHandler
  )

  private val commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
    command match {
      case cu: CreateUser => if (state.has(cu.email)) {
        cu.replyTo ! StatusReply.error("email already in use")
        Effect.none
      } else {
        Effect.persist(cu.asEvent).thenRun(_ => cu.replyTo ! StatusReply.success(cu.asEvent.asEntity.asResponse))
      }
      case uu: UpdateUser => Effect.persist(uu.asEvent)
      case du: DeleteUser => Effect.persist(du.asEvent)
      case _ => Effect.unhandled
    }
  }

  private val eventHandler: (State, Event) => State = { (state, event) =>
    event match {
      case uc: UserCreated => state.add(uc.asEntity)
      case uu: UserUpdated =>
        state.get(uu.email).map(u => state.add(u.copy(name = uu.name, email = uu.email, born = uu.born))).getOrElse(state)
      case ud: UserDeleted => state.rem(ud.email)
    }
  }
}

case class UserRoutes(ub: ActorRef[Command])(implicit system: ActorSystem[_]) {

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  implicit val timeout: Timeout = 3.seconds

  val route = handleRejections(ModelValidation.rejectionHandler) {
    pathPrefix("users") {
      post {
        entity(as[RequestCreateUser]) { rcu =>
          validated(rcu, UserRequest.rules) { valid =>
            val result: Future[StatusReply[UserResponse]] = ub.ask(ref => valid.asCmd(ref))
            onSuccess(result) {
              case sur: StatusReply[UserResponse] if sur.isSuccess =>
                complete(
                  HttpResponse(
                    StatusCodes.Created,
                    entity = HttpEntity(ContentTypes.`application/json`, sur.getValue.asJson.toString())
                  )
                )
              case sur: StatusReply[UserResponse] =>
                complete(
                  HttpResponse(
                    StatusCodes.Conflict,
                    entity = HttpEntity(ContentTypes.`application/json`, UserInputError(sur.getError.getMessage).asJson.toString())
                  )
                )
              case _ =>
                complete(HttpResponse(StatusCodes.BadRequest))
            }
          }
        }
      }
    }
  }
}
