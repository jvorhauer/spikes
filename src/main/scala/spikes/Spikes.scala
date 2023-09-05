package spikes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.*
import akka.http.scaladsl.server.Directives.*
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.typesafe.config.{Config, ConfigFactory}
import com.zaxxer.hikari.HikariDataSource
import io.sentry.{Sentry, SentryOptions}
import kamon.Kamon
import scalikejdbc.*
import spikes.behavior.{Manager, SessionReaper}
import spikes.build.BuildInfo
import spikes.model.{Comment, Note, Session, Tag, User}
import spikes.route.*
import spikes.validate.Validation

import javax.sql.DataSource
import scala.concurrent.duration.DurationInt


object Spikes {

  val cfg: Config = ConfigFactory.defaultApplication()
  implicit val session: DBSession = init

  def main(args: Array[String]): Unit = {
    Kamon.init()
    Sentry.init((options: SentryOptions) => {
      options.setEnvironment("production")
      options.setDsn(System.getenv("SENTRY_DSN"))
      options.setRelease(BuildInfo.version)
    })
    ActorSystem[Nothing](apply(), "spikes")
  }

  def apply(port: Int = 8080): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
    implicit val system = ctx.system
    implicit val ec = system.executionContext

    val manager = ctx.spawn(Manager(), "manager")
    ctx.spawn(SessionReaper(manager, 1.minute), "session-reaper")

    val settings = CorsSettings.defaultSettings.withAllowedOrigins(HttpOriginMatcher.*).withAllowedMethods(Seq(POST, GET, PUT, DELETE))
    val routes = handleRejections(Validation.rejectionHandler) {
      cors(settings) {
        concat(UserRouter(manager).route, InfoRouter(manager).route, NoteRouter().route, SessionRouter().route)
      }
    }
    Http(system).newServerAt("0.0.0.0", port).bind(routes).map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))
    Behaviors.empty
  }


  def init: DBSession = {
    val dataSource: DataSource = {
      val ds = new HikariDataSource()
      ds.setDataSourceClassName("org.h2.jdbcx.JdbcDataSource")
      ds.setConnectionTestQuery("VALUES 1")
      ds.addDataSourceProperty("url", "jdbc:h2:mem:spikes") // // setJdbcUrl is BORKEN for datasource! use this line only!!!
      ds.setUsername("sa")
      ds.setPassword("")
      ds
    }
    ConnectionPool.singleton(new DataSourceConnectionPool(dataSource))
    implicit val session: DBSession = AutoSession

    (User.ddl ++ Session.ddl ++ Note.ddl ++ Comment.ddl ++ Tag.ddl).foreach(_.apply())

    session
  }
}
