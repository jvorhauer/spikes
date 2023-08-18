package spikes.repositories

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import scalikejdbc.DBSession
import spikes.Spikes
import spikes.model.{Access, Note, Status, next, now, today}


class NoteRepositorySpec extends AnyWordSpecLike with Matchers with BeforeAndAfterEach {

  private val prefix = s"${today.getYear}${today.getMonthValue}${today.getDayOfMonth}"

  implicit val session: DBSession = Spikes.init

  "A Note.Repository" should {
    "create a new Note" in {
      val nc = Note.Created(next, next, "test", "body", s"$prefix-test", now.plusDays(5), Status.ToDo, Access.Public)
      val r1 = Note.Repository.save(nc)
      r1.id should be (nc.id)

      val r2 = Note.Repository.find(nc.id)
      r2 should not be empty
      r2.get.id should be (nc.id)
      r2.get.title should be ("test")

      val r5 = Note.Repository.find(nc.slug)
      r5 should not be empty
      r5.get.id should be (nc.id)

      val r3 = Note.Repository.list()
      r3 should have size 1

      val r4 = Note.Repository.remove(nc.id)
      r4 should be (true)

      Note.Repository.size() should be (0)
    }
  }

  override def beforeEach(): Unit = Note.Repository.removeAll()
}
