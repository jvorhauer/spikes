package spikes.model

import scalikejdbc.interpolation.SQLSyntax.{count, distinct}
import scalikejdbc.*
import spikes.model.User.UserId
import wvlet.airframe.ulid.ULID

import java.time.LocalDateTime

final case class Session(id: UserId, token: String, expires: LocalDateTime) extends Entity {
  lazy val asOAuthToken: OAuthToken = OAuthToken(token, id)
  def isValid(t: String): Boolean = token.contentEquals(t) && expires.isAfter(now)
}

object Session extends SQLSyntaxSupport[Session] {
  override val tableName = "sessions"
  private val s = Session.syntax("s")
  private val cols = Session.column
  implicit val session: DBSession = AutoSession

  def save(us: User.State, expires: LocalDateTime): Session = {
    find(us.id) match {
      case None =>
        val ses = Session(us.id, hash(next), expires)
        withSQL(insert.into(Session).namedValues(cols.id -> us.id, cols.token -> ses.token, cols.expires -> ses.expires)).update.apply()
        ses
      case Some(es) =>
        withSQL(update(Session).set(cols.expires -> expires).where.eq(cols.id, es.id)).update.apply()
        es
    }
  }
  def find(token: String): Option[Session] = withSQL(select.from(Session as s).where.eq(cols.token, token)).map(Session(_)).single.apply()
  def find(id: UserId): Option[Session] = withSQL(select.from(Session as s).where.eq(cols.id, id)).map(Session(_)).single.apply()
  def list(): List[Session] = withSQL(select.from(Session as s)).map(Session(_)).list.apply()
  def remove(id: UserId): Unit = withSQL(delete.from(Session as s).where.eq(cols.id, id)).update.apply()
  def size(): Int = withSQL(select(count(distinct(cols.id))).from(Session as s)).map(_.int(1)).single.apply().getOrElse(0)
  def expired(): Int = withSQL(select(count(distinct(cols.id))).from(Session as s).where.lt(cols.expires, now)).map(_.int(1)).single.apply().getOrElse(0)
  def reap(): Unit = withSQL(delete.from(Session as s).where.lt(cols.expires, now)).update.apply()

  def reapable(): List[Session] = withSQL(select.from(Session as s).where.lt(cols.expires, now)).map(Session(_)).list.apply()

  def apply(rs: WrappedResultSet): Session = new Session(ULID(rs.string("id")), rs.string("token"), rs.localDateTime("expires"))

  final case class Response(id: UserId, expires: LocalDateTime) extends ResponseT
  object Response {
    def apply(session: Session): Response = new Response(session.id, session.expires)
  }

  val ddl: Seq[SQLExecution] = Seq(
    sql"""create table if not exists sessions (
         id char(26) not null primary key,
         token varchar(255) not null,
         expires timestamp not null
       )""".execute,
    sql"""create unique index if not exists sessions_token_idx on sessions (token)""".execute
  )
}
