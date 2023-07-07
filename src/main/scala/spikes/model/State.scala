package spikes.model

import gremlin.scala.*
import org.scalactic.TripleEquals.*
import wvlet.airframe.ulid.ULID

import java.time.LocalDateTime
import scala.collection.immutable.HashSet

case class State(sessions: Set[User.Session] = HashSet.empty)(implicit val graph: ScalaGraph) {
  private def trav   = graph.traversal.V()
  private def userVs = trav.hasLabel[User]()
  private def notesVs = trav.hasLabel[Note]()

  private val emailKey = Key[String]("email")
  private val idKey = Key[ULID]("id")

  def save(u: User): State =  {
    findUser(u.id) match {
      case Some(user) => user.vertex.foreach(_.updateAs[User](_ => u))
      case None => graph.addVertex(u)
    }
    this
  }
  def findUser(email: String): Option[User] = userVs.has(emailKey, email).headOption().map(_.asScala().toCC[User])
  def findUser(id: ULID): Option[User] = userVs.has(idKey, id).headOption().map(_.asScala().toCC[User])
  def findUsers(): List[User] = userVs.toList().map(_.asScala().toCC[User])
  def deleteUser(id: ULID): State = {
    userVs.has(idKey, id).drop().iterate()
    this.copy(sessions = sessions.filterNot(_.id === id))
  }
  def getUserResponse(id: ULID): Option[User.Response] = findUser(id).map(_.asResponse)
  def userCount: Long = trav.hasLabel[User]().count().head()

  def login(u: User, expires: LocalDateTime): State = this.copy(sessions = sessions + u.asSession(expires))
  def authorize(token: String): Option[User.Session] = sessions.find(_.token === token).filter(_.expires.isAfter(now))
  def authorize(id: ULID): Option[User.Session] = sessions.find(_.id === id).filter(_.expires.isAfter(now))
  def logout(id: ULID): State = this.copy(sessions = sessions.filterNot(_.id === id))

  def follow(id: ULID, other: ULID): State = {
    val ou = findUser(id)
    val oo = findUser(other)
    if (ou.isDefined && oo.isDefined && ou.get.vertex.isDefined && oo.get.vertex.isDefined) {
      ou.get.vertex.foreach(_ --- "follows" --> oo.get.vertex.get)
    }
    this
  }

  def save(n: Note): State = {
    findNote(n.id) match {
      case Some(note) => note.vertex.foreach(_.updateAs[Note](_ => n))
      case None => {
        val vnote = graph.addVertex(n)
        findUser(n.owner).foreach(_.vertex.foreach(_.addEdge("created", vnote)))
      }
    }
    this
  }
  def findNote(id: ULID): Option[Note] = notesVs.has(idKey, id).headOption().map(_.asScala().toCC[Note])
  def deleteNote(id: ULID): State = {
    notesVs.has(idKey, id).drop().iterate()
    this
  }
  def noteCount: Long = notesVs.count().head()
}
