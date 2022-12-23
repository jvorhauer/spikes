package spikes.projection

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import slick.basic.DatabaseConfig
import slick.jdbc.H2Profile
import spikes.model.{Event, User}

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.{DurationInt, FiniteDuration}


class UsersTests extends ScalaTestWithActorTestKit with AnyFlatSpecLike with Matchers {

  private val delay: FiniteDuration = 100.millis

  "inserting" should "increase the count" in {

    val db: DatabaseConfig[H2Profile] = DatabaseConfig.forConfig("h2projection")
    val repo = new UsersRepository(db)
    Await.result(repo.create(), delay)

    val user = User(
      UUID.randomUUID(), "Tester", "test@test.er", "Welkom123!", LocalDateTime.now(), LocalDate.now().minusYears(42)
    )
    Await.result(repo.run(repo.save(user)), delay) should be (1)
    Await.result(repo.count(), delay) should be (1)

    val uu = Event.UserUpdated(user.id, "Boop", "-", LocalDate.now().minusYears(41))
    Await.result(repo.run(repo.update(uu)), delay)
    Await.result(repo.count(), delay) should be (1)

    Await.result(repo.run(repo.delete("test@test.er")), delay)
    Await.result(repo.count(), delay) should be (0)
  }
}
