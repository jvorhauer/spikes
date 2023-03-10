package spikes.model

case class OAuthToken(access_token: String, token_type: String = "bearer", expires_in: Int = 7200) extends Respons
