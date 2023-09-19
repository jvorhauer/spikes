package spikes.repositories

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import scalikejdbc.DBSession
import spikes.Spikes
import spikes.model.{Access, Note, Status, next, now, today}


class NoteRepositorySpec extends AnyWordSpecLike with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  private val prefix = s"${today.getYear}${today.getMonthValue}${today.getDayOfMonth}"

  implicit val session: DBSession = Spikes.init

  "A Note.Repository" should {
    "create a new Note" in {
      val nc = Note.Created(next, next, "test", "body", s"$prefix-test", now.plusDays(5), Status.ToDo, Access.Public)
      val r1 = nc.save
      r1.id should be (nc.id)

      val r2 = Note.find(nc.id)
      r2 should not be empty
      r2.get.id should be (nc.id)
      r2.get.title should be ("test")

      val r5 = Note.find(nc.slug)
      r5 should not be empty
      r5.get.id should be (nc.id)

      val r3 = Note.list()
      r3 should have size 1

      val r4 = Note.remove(nc.id)
      r4 should be (true)

      Note.size should be (0)
    }
  }

  override def beforeEach(): Unit = Note.list(Int.MaxValue).foreach(_.remove())
  override def afterAll(): Unit = beforeEach()
}
