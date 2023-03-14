package spikes.model

import org.scalactic.TripleEquals.*
import wvlet.airframe.ulid.ULID

case class Tasks(ids: Map[ULID, Task] = Map.empty, owners: Map[ULID, Set[Task]] = Map.empty) {
  val save: Task => Tasks = task => Tasks(ids + (task.id -> task), owners + (task.owner -> mine(task.owner).filter(_.id !== task.id).incl(task)))
  val find: ULID => Option[Task] = id => ids.get(id)
  val mine: ULID => Set[Task] = id => owners.getOrElse(id, Set.empty)
  val remove: ULID => Tasks = id => find(id).map(task => Tasks(ids.removed(id), owners + (task.owner -> mine(task.owner).filter(_.id !== task.id)))).getOrElse(this)
  val disown: ULID => Tasks = id => Tasks(ids -- mine(id).map(_.id), owners.removed(id))
  lazy val all = ids.values
  lazy val size = ids.size
}
