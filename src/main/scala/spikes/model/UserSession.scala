package spikes.model

import wvlet.airframe.ulid.ULID

import java.time.LocalDateTime

case class UserSession(token: String, id: ULID, expires: LocalDateTime = now.plusHours(2)) {
  lazy val asOAuthToken: OAuthToken = OAuthToken(token)
}
