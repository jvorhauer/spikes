package spikes.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.behavior.TestUser
import wvlet.airframe.ulid.ULID

class UsersTests extends AnyFlatSpec with Matchers with TestUser {

  "Users" should "add and remove a User" in {
    val user = User(ulid, name, email, password, born)
    val users = Users()
    users should have size 0
    users.valid should be (true)

    val users1 = users.save(user)
    users1 should have size 1
    users1.valid should be (true)

    val users2 = users1.remove(ulid)
    users2 should have size 0
    users2.valid should be (true)
  }

  "Users" should "add many and remain valid" in {
    val users = (1 to 1000).toList.map(i =>
      User(ULID.newULID, fakeName, fakeEmail, fakePassword, born.minusDays(i))
    ).foldLeft(Users())((acc, user) => acc.save(user))
    users should have size 1000
    users.valid should be (true)

    val users1 = users.remove(ULID.newULID) // would be incredible ;-)
    users1 should have size 1000
    users1.valid should be(true)
  }

  "Find a User" should "only find existing" in {
    val user = User(ulid, name, email, password, born)
    val users1 = (1 to 1000)
      .toList
      .map(i => User(ULID.newULID, fakeName, fakeEmail, fakePassword, born.minusDays(i)))
      .foldLeft(Users())((acc, user) => acc.save(user))
    val users2 = users1.save(user)
    val users = users2.concat(
      (1 to 1000)
        .toList
        .map(i => User(ULID.newULID, fakeName, fakeEmail, fakePassword, born.minusDays(i)))
        .foldLeft(Users())((acc, user) => acc.save(user))
    )

    users should have size 2001
    users.valid should be(true)

    users.find(ulid) should not be None
    users.find(email) should not be None
    users.find(ULID.newULID) should be (None)
    users.find("bill.gates@microsoft.com") should be (None)
  }

  "Add a User twice" should "result in one" in {
    val u1 = User(ulid, name, email, password, born)
    val users1 = Users().save(u1)
    users1 should have size 1
    users1.valid should be (true)
    val users2 = users1.save(u1)
    users2 should have size 1
    users2.valid should be(true)
  }

  "Add and Update a User" should "result in one updated User" in {
    val u1 = User(ulid, name, email, password, born)
    val users1 = Users().save(u1)
    users1 should have size 1
    users1.valid should be(true)
    val u2 = u1.copy(name = "Oink")
    val users2 = users1.save(u2)
    users2 should have size 1
    users2.valid should be(true)
    val found = users2.find(u2.id)
    found should not be None
    found.get.name should be (u2.name)
  }
}
