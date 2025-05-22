package url.shortener.infrastructure.persistence

import org.springframework.stereotype.Repository
import url.shortener.domain.model.UrlMapping
import url.shortener.domain.repository.UrlMappingRepository

@Repository
class UrlMappingRepositoryImpl(
    private val cassandraRepository: CassandraUrlMappingRepository
) : UrlMappingRepository {
    override fun find(shortUrl: String): UrlMapping? {
        return cassandraRepository.findByShortUrl(shortUrl)?.toDomain()
    }

    override fun save(urlMapping: UrlMapping): UrlMapping {
        return cassandraRepository.save(urlMapping.toEntity()).toDomain()
    }

    override fun delete(shortUrl: String): Boolean {
        cassandraRepository.deleteById(shortUrl)
        return true
    }

    override fun exists(shortUrl: String): Boolean {
        return cassandraRepository.existsById(shortUrl)
    }

    private fun UrlMappingEntity.toDomain(): UrlMapping {
        return UrlMapping(
            id = shortUrl,
            longUrl = longUrl,
            creationDate = creationDate,
            expirationDate = expirationDate,
            clickCount = clickCount
        )
    }

    private fun UrlMapping.toEntity(): UrlMappingEntity {
        return UrlMappingEntity(
            shortUrl = id,
            longUrl = longUrl,
            creationDate = creationDate,
            expirationDate = expirationDate,
            clickCount = clickCount
        )
    }
}
