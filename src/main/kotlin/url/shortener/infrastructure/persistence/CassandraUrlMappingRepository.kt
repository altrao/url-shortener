package url.shortener.infrastructure.persistence

import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.cassandra.repository.Query
import url.shortener.domain.model.UrlMapping
import java.time.Instant

interface CassandraUrlMappingRepository : CassandraRepository<UrlMappingEntity, String> {
    fun findByShortUrl(shortUrl: String): UrlMappingEntity?

    @Query("SELECT * FROM url_mappings WHERE expirationdate < ?0 ALLOW FILTERING")
    fun findAllExpired(since: Instant = Instant.now()): List<UrlMappingEntity>
}
