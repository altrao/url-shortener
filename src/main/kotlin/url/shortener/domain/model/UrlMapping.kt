package url.shortener.domain.model

import java.time.Instant

data class UrlMapping(
    val id: String,
    val longUrl: String,
    val creationDate: Instant = Instant.now(),
    val expirationDate: Instant? = null,
    val clickCount: Int = 0
) {
    fun isExpired(): Boolean {
        return expirationDate?.let { Instant.now().isAfter(it) } ?: false
    }
}
