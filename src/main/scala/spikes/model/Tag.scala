package spikes.model

import scalikejdbc.*
import spikes.model.Tag.TagID


final case class Tag(id: TagID, title: String)

object Tag {

  type TagID = SPID

  val ddl: Seq[SQLExecution] = Seq(
    sql"create table if not exists tags (id bigint primary key, title varchar(255) not null)",
    sql"create table if not exists notes_tags (id bigint primary key, note_id bigint not null, tag_id bigint not null)"
  ).map(_.execute)
}
