package spikes.model

import spikes.model.User.UserId

final case class OAuthToken(
    access_token: String,
    id: UserId,
    token_type: String = "bearer",
    expires_in: Int = 7200
) extends ResponseT
