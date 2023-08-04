package spikes.model

import wvlet.airframe.ulid.ULID

case class OAuthToken(access_token: String, id: ULID, token_type: String = "bearer", expires_in: Int = 7200) extends ResponseT
