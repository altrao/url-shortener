package url.shortener.infrastructure.persistence

import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.cassandra.repository.Query

interface CassandraUrlMappingRepository : CassandraRepository<UrlMappingEntity, String> {
    fun findByShortUrl(shortUrl: String): UrlMappingEntity?

    @Query("SELECT * FROM id WHERE expiration_date < now()")
    fun findAllByExpirationDateBefore(): List<String>
}
