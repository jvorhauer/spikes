package spikes.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.behavior.TestUser
import wvlet.airframe.ulid.ULID

class UsersTests extends AnyFlatSpec with Matchers with TestUser {

  "Users" should "add and remove a User" in {
    val user = User(ulid, name, email, password, born)
    val users = Users()
    assert(users.valid)

    val users1 = users.save(user)
    assert(users.size === 0)
    assert(users1.size === 1)
    assert(users1.valid)

    val users2 = users1.remove(ulid)
    assert(users1.size === 1)
    assert(users1.valid)
    assert(users2.size === 0)
    assert(users2.valid)
  }

  "Users" should "add many and remain valid" in {
    val users = (1 to 1000).toList.map(i =>
      User(ULID.newULID, fakeName, fakeEmail, fakePassword, born.minusDays(i))
    ).foldLeft(Users())((acc, user) => acc.save(user))
    assert(users.size == 1000)
    assert(users.valid)

    val users1 = users.remove(ULID.newULID) // would be incredible ;-)
    assert(users1.size == 1000)
    assert(users1.valid)
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

    assert(users.size == 2001)
    assert(users.valid)

    assert(users.find(ulid).isDefined)
    assert(users.find(email).isDefined)
    assert(users.find(ULID.newULID).isEmpty)
    assert(users.find("bill.gates@microsoft.com").isEmpty)
  }

  "Add a User twice" should "result in one" in {
    val u1 = User(ulid, name, email, password, born)
    val users1 = Users().save(u1)
    assert(users1.size == 1)
    assert(users1.valid)
    val users2 = users1.save(u1)
    assert(users2.size == 1)
    assert(users2.valid)
  }

  "Add and Update a User" should "result in one updated User" in {
    val u1 = User(ulid, name, email, password, born)
    val users1 = Users().save(u1)
    assert(users1.size == 1)
    assert(users1.valid)
    val u2 = u1.copy(name = "Oink")
    val users2 = users1.save(u2)
    assert(users2.size == 1)
    assert(users2.valid)
    val found = users2.find(u2.id)
    assert(found.isDefined)
    assert(found.get.name == u2.name)
  }
}
