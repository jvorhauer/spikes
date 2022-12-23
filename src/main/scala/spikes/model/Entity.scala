package spikes.model

import java.util.UUID

trait Entity extends CborSerializable {
 def id: UUID
}
