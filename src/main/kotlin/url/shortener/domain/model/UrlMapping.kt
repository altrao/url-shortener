package url.shortener.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.cassandra.core.mapping.Table
import java.io.Serializable
import java.time.Instant

@Table
data class UrlMapping(
    @Id
    val id: String,
    val longUrl: String,
    val creationDate: Instant = Instant.now(),
    val expirationDate: Instant? = null,
    val clickCount: Int = 0
): Serializable
