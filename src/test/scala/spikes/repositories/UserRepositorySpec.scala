package spikes.repositories

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import scalikejdbc.DBSession
import spikes.Spikes
import spikes.model.Note.makeslug
import spikes.model.{Access, Note, Status, User, next, now, today}


class UserRepositorySpec extends AnyWordSpecLike with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  implicit val session: DBSession = Spikes.init

  override def beforeEach(): Unit = {
    User.list(Int.MaxValue).foreach(_.remove())
  }

  "A User Repository" should {
    "Add a new User, find and then remove that User" in {
      val uc = User.Created(next, "Tester", "tester@test.er", "Welkom123!", today.minusYears(42), None)
      val r1 = uc.save
      r1.id should be (uc.id)

      val r2 = User.find(uc.id)
      r2 should not be empty
      r2.get.id should be (r1.id)
      r2.get.name should be ("Tester")

      val r3 = User.find("tester@test.er")
      r3 should not be empty
      r3.get.id should be(uc.id)

      val r4 = User.list()
      r4 should have size 1

      val r5 = User.size
      r5 should be (1)

      User.remove(uc.id)
      User.size should be (0)
    }

    "Query an empty store" in {
      val r1 = User.list()
      r1 should have size 0

      User.size should be (0)
    }

    "Add a Note to a new User" in {
      val uc = User.Created(next, "Tester", "tester@test.er", "Welkom123!", today.minusYears(42), None)
      val r1 = uc.save
      r1.id should be(uc.id)

      val noteId = next
      val title = "title"
      val slug = makeslug(noteId, title)
      val nc = Note.Created(noteId, uc.id, "title", "body", slug, now.plusDays(5), Status.ToDo, Access.Public)
      val r2 = Note.save(nc)
      r2.id should be (noteId)

      val r3 = Note.find(slug)
      r3 should not be empty
      r3.get.owner should be (uc.id)

      val r4 = User.find(uc.id)
      r4 should not be empty
      r4.get.id should be (r2.owner)
    }
  }

  override def afterAll(): Unit = {
    User.list(Int.MaxValue).foreach(_.remove())
  }
}
