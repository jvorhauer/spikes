package spikes.model

import scalikejdbc.*
import spikes.model.Relation.RelationID


final case class Relation(id: RelationID, title: String)

object Relation {

  type RelationID = SPID

  val ddl: Seq[SQLExecution] = Seq(
    sql"create table if not exists relations (id bigint primary key, title varchar(255) unique not null)",
    sql"create table if not exists note_relations (id bigint primary key, note_id_src bigint not null, note_id_target bigint not null)",
    sql"create table if not exists user_relations (id bigint primary key, user_id_src bigint not null, user_id_target bigint not null)"
  ).map(_.execute)
}
