package spikes.model

import org.scalatest.concurrent.ScalaFutures
import spikes.SpikesTest

class StateTests extends SpikesTest with ScalaFutures {

  val user: User = User(next, "Findable", "findme@miruvor.nl", "???", now.minusYears(42).toLocalDate)

  "A new User" should "be findable" in {
    val s1 = State()
    val s2 = s1.save(user)
    s2.users should have size 1
    s2.findUser("findme@miruvor.nl") should not be None
  }

  "A User" should "be deletable" in {
    val s1 = State()
    val s2 = s1.save(user)
    s2.users should have size 1
    val s3 = s2.deleteUser(user.id)
    s3.users should have size 0
  }

  "A logged in User" should "have its session removed" in {
    val s1 = State()
    val s2 = s1.save(user)
    s2.users should have size 1
    val s3 = s2.login(user, now.plusHours(1))
    val ous = s3.authorize(user.id)
    ous should not be None
    s3.users should have size 1
    s3.sessions should have size 1
    val s4 = s3.deleteUser(user.id)
    s4.users should have size 0
    s4.sessions should have size 0
  }

  "A User with a Task" should "have a relation" in {
    val s1 = State()
    val s2 = s1.save(user)
    s2.users should have size 1
    val task = Task(next, user.id, "title", "body", now.plusDays(1), Status.New)
    val s3 = s2.save(task)
    s3.tasks should have size 1
    val s4 = s3.remTask(task.id)
    s4.tasks should have size 0
  }

  "A User with many Tasks" should "have an equal number of relations" in {
    val s1 = State()
    val s2 = s1.save(user)
    s2.users should have size 1
    val seq = (1 to 10).map(r => Task(next, user.id, s"title$r", s"body-$r", now.plusDays(1), Status.New))
    val s3 = seq.foldLeft(s1)((acc, value) => acc.save(value))
    s3.tasks should have size 10
  }
}
