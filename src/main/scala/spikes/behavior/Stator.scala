package spikes.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import gremlin.scala.ScalaGraph
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import spikes.model.{Event, Note, State, User}
import gremlin.scala.*

import java.time.LocalDateTime

object Stator {

  implicit private val graph: ScalaGraph = TinkerGraph.open().asScala()

  def apply(state: State = State()): Behavior[Event] = Behaviors.setup { ctx =>
    Behaviors.receiveMessage { msg =>
      ctx.log.info(s"msg: $msg")
      msg match {
        case uc: User.Created => state.save(uc.asEntity)
        case uu: User.Updated => state.findUser(uu.id).map(u => state.save(u.copy(name = uu.name, password = uu.password, born = uu.born))).getOrElse(state)
        case ud: User.Removed => state.deleteUser(ud.id)

        case li: User.LoggedIn if li.expires.isAfter(now) => state.findUser(li.id).map(state.login(_, li.expires)).getOrElse(state)
        case lo: User.LoggedOut                           => state.logout(lo.id)

        case uf: User.Followed => state.follow(uf.id, uf.other)

        case tc: Note.Created => state.save(tc.asNote)
        case tu: Note.Updated => state.save(tu.asNote)
        case tr: Note.Removed => state.deleteNote(tr.id)

        case _: Reaper.Reaped => state.copy(sessions = state.sessions.filter(_.expires.isAfter(now)))
      }
      Behaviors.same
    }
  }

  def now: LocalDateTime = LocalDateTime.now()
}
