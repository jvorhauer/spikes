package spikes.repositories

import org.specs2.mutable.*
import scalikejdbc.specs2.mutable.AutoRollback
import spikes.Spikes
import spikes.model.{Comment, next}

class CommentRepositorySpec extends Specification {

  Spikes.init

  "Comment Repository" should {
    "list by note id" in new AutoRollback {
      val comments = Comment.onNote(next)
      comments.size must beEqualTo(0)
    }

    "save and list a new Comment" in new AutoRollback {
      val cc = Comment.Created(next, next, next, None, "title", "body", None, 3)
      val r1 = cc.save
      r1.title must beEqualTo ("title")

      Comment.onNote(cc.noteId) must haveSize (1)
      Comment.byWriter(cc.writer) must haveSize (1)
    }
  }
}
