package spikes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.*
import kamon.Kamon
import scalikejdbc.*
import spikes.behavior.Manager
import spikes.route.*
import spikes.validate.Validation


object Spikes {

  implicit val session: DBSession = init

  def main(args: Array[String]): Unit = {
    Kamon.init()
    ActorSystem[Nothing](apply(), "spikes")
  }

  def apply(port: Int = 8080): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
    implicit val system = ctx.system

    val manager = ctx.spawn(Manager(), "manager")
    val routes = handleRejections(Validation.rejectionHandler) {
      concat(
        UserRouter(manager).route,
        InfoRouter(manager).route,
        NoteRouter().route
      )
    }
    Http(system).newServerAt("0.0.0.0", port).bind(routes)
    Behaviors.empty
  }


  def init: DBSession = {
    Class.forName("org.h2.Driver")
    ConnectionPool.singleton("jdbc:h2:mem:test", "sa", "")

    implicit val session: DBSession = AutoSession

    sql"""create table if not exists users (
      id varchar(26) not null primary key,
      name varchar(255) not null,
      email varchar(1024) not null,
      password varchar(1024) not null,
      born date not null,
      bio varchar(4096)
    )""".execute.apply()
    sql"""create table if not exists notes (
      id varchar(26) not null primary key,
      owner varchar(26) not null,
      title varchar(255) not null,
      body varchar(1024) not null,
      slug varchar(255) not null,
      due timestamp,
      status int
    )""".execute.apply()
    sql"""create unique index if not exists users_email_idx on users (email)""".execute.apply()
    sql"""create index if not exists notes_owner_idx on notes (owner)""".execute.apply()
    sql"""create unique index if not exists notes_slug_idx on notes (slug)""".execute.apply()

    session
  }
}
