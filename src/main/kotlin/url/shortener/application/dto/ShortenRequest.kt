package url.shortener.application.dto

import java.time.Instant

data class ShortenRequest(
    val longUrl: String,
    val customAlias: String? = null,
    val expirationDate: Instant? = null
)
