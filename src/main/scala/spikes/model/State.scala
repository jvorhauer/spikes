package spikes.model

import gremlin.scala.*
import org.scalactic.TripleEquals.*
import wvlet.airframe.ulid.ULID

import java.time.LocalDateTime
import scala.collection.immutable.HashSet

case class State(sessions: Set[User.Session] = HashSet.empty)(implicit val graph: ScalaGraph) {
  private def trav   = graph.traversal.V()
  private def userVs = trav.hasLabel[User]()
  private def taskVs = trav.hasLabel[Task]()
  private def bmVs   = trav.hasLabel[Bookmark]()

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

  def save(t: Task): State = {
    findTask(t.id) match {
      case Some(task) => task.vertex.foreach(_.updateAs[Task](_ => t))
      case None => {
        val vtask = graph.addVertex(t)
        findUser(t.owner).foreach(_.vertex.foreach(_.addEdge("created", vtask)))
      }
    }
    this
  }
  def findTask(id: ULID): Option[Task] = taskVs.has(idKey, id).headOption().map(_.asScala().toCC[Task])
  def deleteTask(id: ULID): State = {
    taskVs.has(idKey, id).drop().iterate()
    this
  }
  def taskCount: Long = taskVs.count().head()

  def save(b: Bookmark): State = {
    findBookmark(b.id) match {
      case Some(bookmark) => bookmark.vertex.foreach(_.updateAs[Bookmark](_ => b))
      case None => {
        val vbm = graph.addVertex(b)
        findUser(b.owner).foreach(_.vertex.foreach(_.addEdge("created", vbm)))
      }
    }
    this
  }
  def findBookmark(id: ULID): Option[Bookmark] = bmVs.has(idKey, id).headOption().map(_.asScala().toCC[Bookmark])
  def deleteBookmark(id: ULID): State = {
    bmVs.has(idKey, id).drop().iterate()
    this
  }
  def bookmarkCount: Long = bmVs.count().head()
}
