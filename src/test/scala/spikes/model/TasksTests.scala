package spikes.model

import net.datafaker.Faker
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.LocalDateTime
import java.util.Locale

class TasksTests extends AnyFlatSpec with Matchers {

  val faker = new Faker(Locale.US)
  def fakeTitle: String = faker.text().text(23, 72)
  def fakeBody: String = faker.text().text(123, 272)

  val owners = Array(next, next, next, next)
  val ownerId = next

  "Tasks" should "save and retrieve" in {
    val t1 = Tasks()
    t1.ids should have size 0
    t1.owners should have size 0
    val task = Task(next, ownerId, "Test Task", "Test Body", LocalDateTime.now().plusDays(1), Status.ToDo)
    val t2 = t1.save(task)
    t2.all should have size 1

    t2.mine(ownerId) should have size 1
    t2.mine(next) should have size 0

    t2.find(task.id) should not be None
    t2.find(next) should be (None)
  }

  "Add a Task twice" should "store only one" in {
    val t1 = Tasks()
    t1.all should have size 0
    val task = Task(next, ownerId, "Test Task", "Test Body", LocalDateTime.now().plusDays(1), Status.ToDo)
    val t2 = t1.save(task)
    t2.all should have size 1

    val updated = task.copy(title = "Updated Task")
    val t3 = t2.save(updated)
    t3.all should have size 1
    t3.owners should have size 1
    t3.mine(ownerId) should have size 1
    t3.mine(ownerId).head.title should be ("Updated Task")

    val found = t3.find(task.id)
    found should not be None
    found.get.title should be ("Updated Task")
  }

  "Adding loads of Tasks" should "still be ok" in {
    val limit = 1000
    val tasks = (1 to limit).toList.map(i =>
      Task(next, owners(i % owners.length), fakeTitle, fakeBody, LocalDateTime.now().plusDays(i), Status.ToDo)
    ).foldLeft(Tasks())((acc, task) => acc.save(task))
    tasks should have size limit
    tasks.owners should have size owners.length
    tasks.mine(owners(1)) should have size (limit / owners.length)

    val updated = tasks.mine(owners(2)).last.copy(title = "Updated Task")
    val t2 = tasks.save(updated)
    val found = t2.find(updated.id)
    found should not be None
    found.get.title should be ("Updated Task")

    val mineIsUpdatedAsWell = t2.mine(owners(2)).find(_.id === updated.id)
    mineIsUpdatedAsWell should not be None
    mineIsUpdatedAsWell.get.title should be ("Updated Task")

    val t3 = t2.remove(updated.id)
    t3 should have size 999
    t3.mine(updated.owner) should have size 249


  }
}
