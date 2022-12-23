package spikes.projection

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.slick.{SlickHandler, SlickProjection}
import org.slf4j.LoggerFactory
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.H2Profile
import spikes.model._

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class UsersRepository(val dbConfig: DatabaseConfig[H2Profile]) {

  import dbConfig.profile.api._

  val db = dbConfig.db

  private class UsersTable(tag: Tag) extends Table[User](tag, "USERS") {
    def id = column[UUID]("USER_ID", O.PrimaryKey)
    def name = column[String]("NAME")
    def email = column[String]("EMAIL")
    def password = column[String]("PASSWORD")
    def joined = column[LocalDateTime]("JOINED")
    def born = column[LocalDate]("BORN")
    def * = (id, name, email, password, joined, born).mapTo[User]
  }
  private lazy val users = TableQuery[UsersTable]

  def create()(implicit system: ActorSystem[_]): Future[Unit] = {
    SlickProjection.createTablesIfNotExists(dbConfig)
    db.run(users.schema.createIfNotExists)
  }

  def run(a: DBIOAction[_, NoStream, Nothing]) = db.run(a)

  def save(u: User) = users.insertOrUpdate(u)
  def count() = db.run(users.size.result)
  def delete(email: String) = users.filter(_.email === email).delete
  def all(): Future[Seq[User]] = db.run(users.result)
  def update(uu: Event.UserUpdated) = users.filter(_.id === uu.id).map(u => (u.name, u.born)).update((uu.name, uu.born))
}

object UsersRepository {
  def apply(dbc: DatabaseConfig[H2Profile]) = new UsersRepository(dbc)
}

class UsersRepositoryHandler(repo: UsersRepository)(implicit ec: ExecutionContext) extends SlickHandler[EventEnvelope[Event]] {

  private val logger = LoggerFactory.getLogger(getClass)

  override def process(envelope: EventEnvelope[Event]): DBIO[Done] = {
    logger.info(s"process: incoming event envelope: ${envelope.event}, ${envelope.timestamp}/${envelope.sequenceNr}")
    envelope.event match {
      case uc: Event.UserCreated => repo.save(uc.asEntity).map(_ => Done)
      case ud: Event.UserDeleted => repo.delete(ud.email).map(_ => Done)
      case uu: Event.UserUpdated => repo.update(uu).map(_ => Done)
      case other =>
        logger.info(s"users handler received event $other, ${repo.count()} users")
        DBIO.successful(Done)
    }
  }
}
