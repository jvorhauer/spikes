package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.hypersistence.tsid.TSID
import io.scalaland.chimney.dsl.TransformerOps
import scalikejdbc.*
import spikes.model.Tag.TagID
import spikes.validate.Validation.{ErrorInfo, validate}


final case class Tag(id: TagID, title: String, color: String = "000000") extends SpikeSerializable {
  def toResponse: Tag.Response = Tag.Response(id, title, color)
  def remove(): Unit = Tag.remove(id)
}

object Tag extends SQLSyntaxSupport[Tag] {

  type TagID = SPID
  type ReplyTo = ActorRef[StatusReply[Tag.Response]]

  override val tableName = "tags"
  implicit val session: DBSession = AutoSession
  private val t = Tag.syntax("t")
  private val cols = Tag.column

  def save(tc: Tag.Created): Tag = {
    withSQL(insert.into(Tag).namedValues(cols.id -> tc.id, cols.title -> tc.title, cols.color -> tc.color)).update.apply()
    Tag(tc.id, tc.title)
  }
  def save(tu: Tag.Updated): Option[Int] = find(tu.id).map(_ =>
    withSQL(update(Tag).set(cols.title -> tu.title, cols.color -> tu.color).where.eq(cols.id, tu.id)).update.apply()).orElse(None)

  def find(id: TagID): Option[Tag] = withSQL(select.from(Tag as t).where.eq(cols.id, id)).map(Tag(_)).single.apply()
  def find(title: String): Option[Tag] = withSQL(select.from(Tag as t).where.eq(cols.title, title)).map(Tag(_)).single.apply()
  def list: List[Tag] = withSQL(select.from(Tag as t)).map(Tag(_)).list.apply()

  def remove(id: TagID): Unit = withSQL(delete.from(Tag).where.eq(cols.id, id)).update.apply()


  final case class Post(title: String, color: String = "000000") extends Request {
    override def validated: Set[ErrorInfo] = Set(validate("title", title), validate("color", color)).flatten
    def toCmd(replyTo: ReplyTo): Create = Create(next, title, color, replyTo)
  }
  final case class Put(id: TagID, title: String, color: String) extends Request {
    override def validated: Set[ErrorInfo] = Set(validate("title", title), validate("color", color)).flatten
    def toCmd(replyTo: ReplyTo): Update = Update(id, title, color, replyTo)
  }
  final case class Delete(id: TagID) extends Request {
    def toCmd(replyTo: ReplyTo): Remove = Remove(id, replyTo)
  }

  final case class Create(id: TagID, title: String, color: String, replyTo: ReplyTo) extends Command {
    def toEvent: Created = this.transformInto[Tag.Created]
    def toResponse: Response = this.transformInto[Tag.Response]
  }
  final case class Update(id: TagID, title: String, color: String, replyTo: ReplyTo) extends Command {
    def toEvent: Updated = this.transformInto[Tag.Updated]
    def toResponse: Response = this.transformInto[Tag.Response]
  }
  final case class Remove(id: TagID, replyTo: ReplyTo) extends Command {
    def toEvent: Removed = this.transformInto[Removed]
  }

  final case class Created(id: TagID, title: String, color: String) extends Event {
    def save: Tag = Tag.save(this)
  }
  final case class Updated(id: TagID, title: String, color: String) extends Event {
    def save: Boolean = Tag.save(this).isDefined
  }
  final case class Removed(id: TagID) extends Event

  final case class Response(id: TagID, title: String, color: String) extends ResponseT

  def apply(rs: WrappedResultSet) = new Tag(
    TSID.from(rs.long("id")),
    rs.string("title"),
    rs.string("color")
  )

  val ddl: Seq[SQLExecution] = Seq(
    sql"create table if not exists tags (id bigint primary key, title varchar(255) not null, color char(6) not null)",
    sql"create index if not exists tags_title_idx on tags (title)"
  ).map(_.execute)
}
