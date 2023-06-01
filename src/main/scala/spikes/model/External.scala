package spikes.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import gremlin.scala.underlying
import org.apache.tinkerpop.gremlin.structure.Vertex
import spikes.validate.Validation
import wvlet.airframe.ulid.ULID

case class External( id: ULID, body: String, @underlying vertex: Option[Vertex] = None ) extends Entity {
  def asResponse = External.Response(id, body)
}

object External {

  type ReplyTo = ActorRef[StatusReply[External.Response]]

  case class Post( body: String ) extends Request {
    lazy val validated: Set[Validation.FieldErrorInfo] = Set()
    def asCmd( replyTo: ReplyTo ) = Create( ULID.newULID, body, replyTo )
  }

  case class Create( id: ULID, body: String, replyTo: ReplyTo ) extends Command {
    def asResponse: Response = Response( id, body )
    def asEvent = Created( id, body )
  }
  case class Find(i: ULID, replyTo: ReplyTo) extends Command

  case class Created( id: ULID, body: String ) extends Event {
    def asEntity: External = External( id, body )
  }

  case class Response( id: ULID, body: String ) extends Respons
}
