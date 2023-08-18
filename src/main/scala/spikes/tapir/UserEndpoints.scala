package spikes.tapir

import akka.actor.typed.receptionist.Receptionist.Find
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import io.circe.generic.auto.*
import spikes.model.{Command, OAuthToken, User}
import sttp.tapir.*
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.json.circe.*
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.server.{PartialServerEndpoint, ServerEndpoint}
import wvlet.airframe.ulid.ULID

import scala.concurrent.{ExecutionContext, Future}

final case class UserEndpoints(manager: ActorRef[Command])(implicit system: ActorSystem[Nothing]) {

  type LookupResult = Future[Option[ActorRef[Command]]]

  implicit lazy val ulidSchema : Schema[ULID]  = Schema(SchemaType.SString())
  implicit lazy val schema: Schema[User.Response] = Schema.derived
  implicit lazy val postSchema: Schema[User.Post] = Schema.derived.validate(Validator.custom(up => ValidationResult.validWhen(up.validated.isEmpty)))
  implicit lazy val putSchema: Schema[User.Put] = Schema.derived.validate(Validator.custom(up => ValidationResult.validWhen(up.validated.isEmpty)))
  implicit lazy val loginSchema: Schema[User.Authenticate] = Schema.derived
  implicit lazy val tokenSchema: Schema[OAuthToken] = Schema.derived
  implicit lazy val sessionSchema: Schema[User.Session] = Schema.derived

  implicit val ec: ExecutionContext = system.executionContext

  def sur2either(sur: StatusReply[User.Response]): Either[String, User.Response] = if (sur.isSuccess) Right(sur.getValue) else Left(sur.getError.getMessage)
  def lookup(str: String, key: ServiceKey[Command]): LookupResult = {
    system.receptionist.ask(Find(key)).map(_.serviceInstances(key).find(_.path.name.contains(str)))
  }
  def decode(s: String): DecodeResult[ULID] = if (ULID.isValid(s)) {
    DecodeResult.Value(ULID(s))
  } else {
    DecodeResult.Error(s, InvalidULIDRejection("not a valid ULID"))
  }
  def encode(ulid: ULID): String = ulid.toString
  implicit val ulidCodec: Codec[String, ULID, TextPlain] = Codec.string.mapDecode(decode)(encode)



  def authenticate(token: String): Future[Either[String, User.Session]] = lookup(token, User.key).flatMap {
    case Some(ar) => ar.ask(User.TapLogin(token, _))
    case None => Future.failed(AuthenticationError("invalid credentials"))
  }
  val secureEndpoint: PartialServerEndpoint[String, User.Session, Unit, String, Unit, Any, Future] =
    endpoint.securityIn(auth.bearer[String]()).errorOut(stringBody).serverSecurityLogic(authenticate)



  val userListing: PublicEndpoint[Int, String, List[User.Response], Any] = endpoint
      .get.in("susers")
      .in(query[Int]("limit").description("max nr of users to return"))
      .errorOut(stringBody)
      .out(jsonBody[List[User.Response]])


  def userListingLogic(limit: Int): Future[Either[String, List[User.Response]]] =
    Future.successful(Right(User.Repository.list(limit = limit).map(User.Response(_))))

  val userPost: PublicEndpoint[User.Post, String, User.Response, Any] = endpoint
    .post.in("susers")
    .in(jsonBody[User.Post])
    .errorOut(stringBody)
    .out(jsonBody[User.Response])

  def userPostLogic(post: User.Post): Future[Either[String, User.Response]] = manager.ask(post.asCmd).map(sur2either)

  val userPut: ServerEndpoint[Any, Future] = secureEndpoint
    .put.in("susers")
    .in(jsonBody[User.Put])
    .out(jsonBody[User.Response])
    .serverLogic(_ => up => userPutLogic(up))
  def userPutLogic(put: User.Put): Future[Either[String, User.Response]] = lookup(put.id.toString, User.key).flatMap {
      case Some(ar) => ar.ask(put.asCmd).map(sur2either)
      case None => Future.failed(NotFoundRejection(s"User with id ${put.id} not found"))
    }

  val userGet: PublicEndpoint[ULID, String, User.Response, Any] = endpoint
    .get.in("susers" / path[ULID]("id"))
    .errorOut(stringBody)
    .out(jsonBody[User.Response])
  def userGetLogic(id: ULID): Future[Either[String, User.Response]] = User.Repository.find(id) match {
      case Some(ur) => Future.successful(Right(User.Response(ur)))
      case None => Future.failed(NotFoundRejection(s"User with id $id not found"))
    }

  val loginPost: PublicEndpoint[User.Authenticate, String, OAuthToken, Any] = endpoint.post
    .in("susers" / "login")
    .in(jsonBody[User.Authenticate])
    .errorOut(stringBody)
    .out(jsonBody[OAuthToken])
  def loginLogic(ua: User.Authenticate): Future[Either[String, OAuthToken]] = lookup(ua.email, User.key).flatMap {
    case Some(ar) => ar.ask(ua.asCmd).map(sur => if (sur.isSuccess) Right(sur.getValue) else Left(sur.getError.getMessage))
    case None => Future.failed(NotFoundRejection(s"User with email ${ua.email} not found"))
  }

  val route: Route = AkkaHttpServerInterpreter().toRoute(List(
    userListing.serverLogic(userListingLogic),
    userPost.serverLogic(userPostLogic),
    userPut,
    userGet.serverLogic(userGetLogic),
    loginPost.serverLogic(loginLogic)
  ))
}

final case class NotFoundRejection(reason: String) extends RuntimeException
final case class InvalidULIDRejection(reason: String) extends RuntimeException
final case class AuthenticationError(why: String) extends RuntimeException
