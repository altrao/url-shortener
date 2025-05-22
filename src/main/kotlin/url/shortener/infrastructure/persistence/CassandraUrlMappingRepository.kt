package url.shortener.infrastructure.persistence

import org.springframework.data.cassandra.repository.CassandraRepository

interface CassandraUrlMappingRepository : CassandraRepository<UrlMappingEntity, String> {
    fun findByShortUrl(shortUrl: String): UrlMappingEntity?
}
