package wip

import gremlin.scala.*
import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory
import spikes.SpikesTest
import spikes.model.{Status, Task, User, next}

import java.time.{LocalDate, LocalDateTime}
import scala.jdk.CollectionConverters.*

class GraphTests extends SpikesTest {

  implicit val graph: ScalaGraph = TinkerFactory.createModern.asScala()

  val name: Key[String] = Key[String]("name")
  val age: Key[String] = Key[String]("age")

  "traversal" should "show persons" in {
    val g = graph.traversal
    g.V().hasLabel("person").toIterator().foreach { v =>
      v.keys should have size 2
      v.asScala().id[Int] should be >= 1
      v.label should be ("person")
      v.toCC[Person].vertex should not be empty
    }

    val me = Person("Jurjen", 55)
    graph.addVertex[Person](me)
//    g.V().hasLabel("person").toIterator().foreach { v =>
//      val sv = v.asScala()
//      val p = v.toCC[Person]
//      println(s"person: $p")
//      sv.out().hasLabel("software").toIterator().foreach( v2 =>
//        println(s"  ${v2.label()}: ${v2.toCC[Software]}")
//      )
//    }
    g.V().hasLabel("person").toList() should have size 5

    val user = User(next, "Harry", "harry@miruvor.nl", "geheim", LocalDate.now().minusYears(23))
    val task = Task(next, user.id, "Test", "Test tekst", LocalDateTime.now().plusDays(1), Status.ToDo)
    val vuser = graph.addVertex[User](user)
    val vtask = graph.addVertex[Task](task)
    vuser.addEdge("created", vtask, "weight", 1.0)
    g.V().hasLabel[User]().toIterator().foreach { v =>
      val sv = v.asScala()
      val u = sv.toCC[User]
      u.name should be("Harry")
      u.email should be("harry@miruvor.nl")
      u.password should be("geheim")
      u.tasks should have size 1
    }
  }

  "edges from one person to another" should "be labelled" in {
    val p1 = Person("Tester", 33)
    val p2 = Person("Other", 34)
    graph.addVertex(p1)
    graph.addVertex(p2)

    def trav = graph.traversal.V().hasLabel("person")
    val on1 = trav.has(name, "Tester").headOption().map(_.asScala().toCC[Person])
    on1 should not be empty
    on1.get.name should be("Tester")
    val on2 = trav.has(name, "Other").headOption().map(_.asScala().toCC[Person])
    on2 should not be empty
    on2.get.name should be("Other")

    on1.get.vertex should not be empty
    on2.get.vertex should not be empty
    on1.get.vertex.get.addEdge("follow", on2.get.vertex.get)

    on1.get.vertex.get.edges(Direction.OUT).hasNext should be(true)
    on1.get.vertex.get.edges(Direction.IN).hasNext should be(false)
    on2.get.vertex.get.edges(Direction.IN).hasNext should be(true)
    on2.get.vertex.get.edges(Direction.OUT).hasNext should be(false)

    on1.get.vertex.get.edges(Direction.OUT, "follow").hasNext should be(true)
    var other = on1.get.vertex.get.edges(Direction.OUT, "follow").next().inVertex().asScala().toCC[Person]
    other.name should be("Other")
    on1.get.vertex.get.out("follow", "person").headOption() should not be empty
    other = on1.get.vertex.get.out("follow", "person").headOption().get.asScala().toCC[Person]
    other.name should be("Other")

    on2.get.vertex.get.edges(Direction.IN, "follow").hasNext should be(true)
    val tester = on2.get.vertex.get.edges(Direction.IN, "follow").next().outVertex().asScala().toCC[Person]
    tester.name should be("Tester")

    val opt = on1.get.vertex.map(_.out("follow", "person").headOption().map(_.asScala().toCC[Person]))
    opt should not be empty
  }

  "edges from person to software" should "be labelled" in {
    graph addVertex Person("Owner", 666)
    graph addVertex Software("Troep", "Dutch")
    graph addVertex Software("Ellende", "Dutch")

    val Purchased = Key[LocalDateTime]("purchased")

    val op1 = graph.traversal.V().hasLabel("person").has(name, "Owner").headOption().map(_.asScala().toCC[Person])
    op1 should not be empty
    val p1 = op1.get

    val os1 = graph.traversal.V().hasLabel("software").has(name, "Troep").headOption().map(_.asScala().toCC[Software])
    os1 should not be empty
    val s1 = os1.get

    val os2 = graph.traversal.V().hasLabel("software").has(name, "Ellende").headOption().map(_.asScala().toCC[Software])
    os2 should not be empty
    val s2 = os2.get

    p1.addEdgeTo(s1.vertex.get, "owns")
    p1.sv --- ("bought", Purchased -> LocalDateTime.now()) --> s2.vertex.get

    p1.vertex.get.edges(Direction.OUT, "owns").hasNext should be(true)
    val e = p1.vertex.get.edges(Direction.OUT, "owns").next()
    e.label() should be("owns")
    e.inVertex().asScala().toCC[Software].name should be("Troep")
    e.outVertex().asScala().toCC[Person].name should be("Owner")

    List.from(p1.getEdges("owns")) should have size 1
    List.from(p1.getEdges("bought")) should have size 1
    List.from(p1.getEdges).flatMap(edge => List.from(edge.bothVertices().asScala)).distinct should have size 3

    p1.vertex.get.asScala().out("owns").count().head() should be(1L)
    p1.vertex.get.asScala().out("bought").count().head() should be(1L)
    p1.vertex.get.asScala().out().count().head() should be(2L)

    val o = p1.vertex.get.asScala().out("owns").headOption().map(_.asScala().toCC[Software])
    o should not be empty
    o.get.name should be("Troep")
  }
}

@label("person")
case class Person(name: String, age: Int, @underlying vertex: Option[Vertex] = None) {
  def addEdgeTo(v: Vertex, s: String)(implicit graph: ScalaGraph): Unit = vertex.foreach(_.asScala() --- s --> v)
  def getEdges: Iterator[Edge] = vertex.map(_.edges(Direction.BOTH).asScala).getOrElse(Iterator.empty)
  def getEdges(label: String): Iterator[Edge] = vertex.map(_.edges(Direction.BOTH, label).asScala).getOrElse(Iterator.empty)
  lazy val sv: ScalaVertex = vertex.get.asScala()
}

@label("software")
case class Software(name: String, lang: String, @underlying vertex: Option[Vertex] = None)
