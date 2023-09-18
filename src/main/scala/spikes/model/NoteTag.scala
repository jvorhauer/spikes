package spikes.model

import scalikejdbc.*
import spikes.model.Note.NoteId
import spikes.model.NoteTag.NoteTagID
import spikes.model.Tag.TagID

final case class NoteTag(id: NoteTagID, noteId: NoteId, tagId: TagID)

object NoteTag {

  type NoteTagID = SPID

  val ddl: Seq[SQLExecution] = Seq(
    sql"create table if not exists notes_tags (id bigint primary key, note_id bigint not null, tag_id bigint not null)"
  ).map(_.execute)
}
