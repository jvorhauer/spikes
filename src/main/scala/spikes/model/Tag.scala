package spikes.model

import scalikejdbc.*

object Tag {

  val ddl: Seq[SQLExecution] = Seq(
    sql"create table if not exists tags (id varchar(26) not null primary key, title varchar(255) not null)".execute,
    sql"create table if not exists notes_tags (id char(26) not null primary key, note_id char(26) not null, tag_id char(26) not null)".execute
  )
}
