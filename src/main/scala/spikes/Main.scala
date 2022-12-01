package inherit

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ValidationRejection
import com.wix.accord
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.io.StdIn

object Main {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "spike")
    Http().newServerAt("127.0.0.1", 8080).bindFlow(route)
    StdIn.readLine("Hit ENTER to exit")
    system.terminate()
  }

  private def route(implicit system: ActorSystem[Nothing]) = {
    pathSingleSlash {
      post {
        entity(as[RequestCreateUser]) { rcu =>
          accord.validate(rcu) match {
            case accord.Success => complete(rcu)
            case accord.Failure(vs) => reject(
              ValidationRejection(vs.map(_.toString.replaceFirst("value with value \"RequestCreateUser", "\"")).mkString(" | "))
            )
          }
        }
      }
    }
  }
}
