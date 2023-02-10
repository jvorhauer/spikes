package spikes.db

import slick.jdbc.H2Profile.api._
import spikes.model._
import wvlet.airframe.ulid.ULID

import java.time.LocalDate
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}

object Repository {

  private val timeout = 100.milliseconds

  implicit val ulidMapper = MappedColumnType.base[ULID, String](
    ulid => ulid.toString(),
    str => ULID.fromString(str)
  )

  class UsersTable(tag: Tag) extends Table[(ULID, String, String, String, LocalDate)](tag, "USERS") {
    def id = column[ULID]("USER_ID", O.PrimaryKey)
    def name = column[String]("NAME")
    def email = column[String]("EMAIL", O.Unique)
    def password = column[String]("PASSWORD")
    def born = column[LocalDate]("BORN")
    def * = (id, name, email, password, born)
  }

  class EntriesTable(tag: Tag) extends Table[(ULID, ULID, String, String, Option[String])](tag, "ENTRIES") {
    def id = column[ULID]("ENTRY_ID", O.PrimaryKey)
    def ownerId = column[ULID]("OWNER_ID")
    def title = column[String]("TITLE")
    def body = column[String]("BODY")
    def url = column[Option[String]]("URL")
    def * = (id, ownerId, title, body, url)
    def owner = foreignKey("ENTRY_USER_FK", ownerId, Repository.users)(_.id)
  }

  class CommentsTable(tag: Tag) extends Table[(ULID, ULID, ULID, String, String)](tag, "COMMENTS") {
    def id = column[ULID]("COMMENT_ID", O.PrimaryKey)
    def entryId = column[ULID]("ENTRY_ID")
    def ownerId = column[ULID]("OWNER_ID")
    def title = column[String]("TITLE")
    def body = column[String]("BODY")
    def * = (id, entryId, ownerId, title, body)
    def entry = foreignKey("COMMENT_ENTRY_FK", entryId, Repository.entries)(_.id)
    def owner = foreignKey("COMMENT_OWNER_FK", ownerId, Repository.users)(_.id)
  }

  private val db = Database.forConfig("h2mem")

  private val users = TableQuery[UsersTable]
  private val usersSlice = Compiled((skip: ConstColumn[Long], rows: ConstColumn[Long]) => users.drop(skip).take(rows))
  private val entries = TableQuery[EntriesTable]
  private val comments = TableQuery[CommentsTable]

  private def await[T](f: Future[T], t: FiniteDuration = timeout): T = Await.result(f, t)

  {
    val setup = DBIO.seq((users.schema ++ entries.schema ++ comments.schema).createIfNotExists)
    await(db.run(setup), 5.seconds)
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  private def save(ins: DBIO[Int]): Int = await(db.run(ins))
  def save(u: User): Int = save(users.insertOrUpdate(u.asTuple))
  def save(e: Entry): Int = save(entries.insertOrUpdate(e.asTuple))
  def save(c: Comment): Int = save(comments.insertOrUpdate(c.asTuple))

  private def enrich(user: User): User = user.copy(entries = await(db.run(entries.filter(_.ownerId === user.id).result)).map(new Entry(_)).map(enrich))
  private def enrich(entry: Entry): Entry = entry.copy(comments = await(db.run(comments.filter(_.entryId === entry.id).result)).map(new Comment(_)))
  def findUser(userId: ULID): Option[User] = await(db.run(users.filter(_.id === userId).result)).headOption.map(new User(_)).map(enrich)
  def findUser(email: String): Option[User] = await(db.run(users.filter(_.email === email).result)).headOption.map(new User(_)).map(enrich)
  def findEntry(id: ULID): Option[Entry] = await(db.run(entries.filter(_.id === id).result)).headOption.map(new Entry(_)).map(enrich)

  def findUsers(skip: Long = 0, rows: Long = Long.MaxValue): Seq[User] = await(
    db.run(usersSlice(skip, rows).result.map(seqt => seqt.map(t => new User(t)).map(enrich)))
  )
  def userCount() = await(db.run(users.length.result))

  private def deleteComment(entryId: ULID): Int = await(db.run(comments.filter(_.entryId === entryId).delete))
  private def deleteEntries(userId: ULID): Int = await(db.run(entries.filter(_.ownerId === userId).delete))
  def deleteUser(userId: ULID): Int = {
    findUser(userId).map(u => {
      u.entries.foreach(e => deleteComment(e.id))
      deleteEntries(u.id)
    })
    await(db.run(users.filter(_.id === userId).delete))
  }

  def reset(): Unit = {
    await(
      db.run(comments.delete)
        .flatMap(_ => db.run(entries.delete))
        .flatMap(_ => db.run(users.delete))
    )
  }
}
