package spikes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.*
import akka.http.scaladsl.server.Directives.*
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.zaxxer.hikari.HikariDataSource
import io.sentry.{Sentry, SentryOptions}
import kamon.Kamon
import scalikejdbc.*
import spikes.behavior.{Manager, SessionReaper}
import spikes.build.BuildInfo
import spikes.route.*
import spikes.validate.Validation

import javax.sql.DataSource
import scala.concurrent.duration.DurationInt


object Spikes {

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

    val manager = ctx.spawn(Manager(), "manager")
    ctx.spawn(SessionReaper(manager, 1.minute), "session-reaper")

    val settings = CorsSettings.defaultSettings.withAllowedOrigins(HttpOriginMatcher.*).withAllowedMethods(Seq(POST, GET, PUT, DELETE))
    val routes = handleRejections(Validation.rejectionHandler) {
      cors(settings) {
        concat(UserRouter(manager).route, InfoRouter(manager).route, NoteRouter().route)
      }
    }
    Http(system).newServerAt("0.0.0.0", port).bind(routes)
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

    sql"""create table if not exists users (
      id char(26) not null primary key,
      name varchar(255) not null,
      email varchar(1024) not null,
      password varchar(1024) not null,
      born date not null,
      bio varchar(4096)
    )""".execute.apply()
    sql"create unique index if not exists users_email_idx on users (email)".execute.apply()

    sql"""create table if not exists sessions (
         id char(26) not null primary key,
         token varchar(255) not null,
         expires timestamp not null
       )""".execute.apply()
    sql"""create unique index if not exists sessions_token_idx on sessions (token)""".execute.apply()

    sql"""create table if not exists notes (
      id char(26) not null primary key,
      owner char(26) not null,
      title varchar(255) not null,
      body varchar(1024) not null,
      slug varchar(255) not null,
      due timestamp,
      status int,
      access int
    )""".execute.apply()
    sql"create index if not exists notes_owner_idx on notes (owner)".execute.apply()
    sql"create unique index if not exists notes_slug_idx on notes (slug)".execute.apply()

    sql"""create table if not exists comments (
         id char(26) not null primary key,
         writer char(26) not null,
         note_id char(26) not null,
         parent char(26),
         title varchar(255) not null,
         body varchar(1024) not null,
         color varchar(6),
         stars tinyint not null
    )""".execute.apply()
    sql"create index if not exists comments_note_idx on comments (note_id)".execute.apply()
    sql"create index if not exists comments_writer_idx on comments (writer)".execute.apply()

    sql"create table if not exists tags (id varchar(26) not null primary key, title varchar(255) not null)".execute.apply()
    sql"""create table if not exists notes_tags (
         id char(26) not null primary key,
         note_id char(26) not null,
         tag_id char(26) not null
    )""".execute.apply()

    session
  }
}
