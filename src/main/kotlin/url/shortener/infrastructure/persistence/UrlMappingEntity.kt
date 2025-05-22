package url.shortener.infrastructure.persistence

import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.Table
import java.time.Instant

@Table("url_mappings")
data class UrlMappingEntity(
    @PrimaryKey
    val shortUrl: String,
    val longUrl: String,
    val creationDate: Instant,
    val expirationDate: Instant?,
    val clickCount: Int
)
