package url.shortener.infrastructure.persistence

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.cassandra.DataCassandraTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import url.shortener.CassandraTestContainerConfiguration
import url.shortener.domain.model.UrlMapping
import url.shortener.domain.repository.UrlMappingRepository
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@DataCassandraTest
@RunWith(SpringRunner::class)
@EnableAutoConfiguration
@ContextConfiguration(classes = [CassandraTestContainerConfiguration::class, UrlMappingRepositoryImpl::class])
class UrlMappingRepositoryImplTest {
    @Autowired
    private lateinit var repository: UrlMappingRepository

    @Test
    fun `should save and find url mapping`() {
        val urlMapping = UrlMapping(
            id = "basic-mapping",
            longUrl = "https://example.com",
            expirationDate = null
        )

        val saved = repository.save(urlMapping)
        val found = repository.find("basic-mapping")

        assertNotNull(found)
        assertEquals(urlMapping.id, saved.id)
        assertEquals(urlMapping.longUrl, saved.longUrl)
        assertEquals(urlMapping.id, found?.id)
        assertEquals(urlMapping.longUrl, found?.longUrl)
    }

    @Test
    fun `should return null when not found`() {
        assertNull(repository.find("non-existent"))
    }

    @Test
    fun `should check existence`() {
        val urlMapping = UrlMapping(
            id = "existent",
            longUrl = "https://example.org",
            expirationDate = null
        )

        repository.save(urlMapping)

        assertTrue(repository.exists("existent"))
        assertFalse(repository.exists("non-existent"))
    }

    @Test
    fun `should delete mapping`() {
        val urlMapping = UrlMapping(
            id = "delete-mapping",
            longUrl = "https://example.com",
            expirationDate = null
        )

        repository.save(urlMapping)

        assertTrue(repository.delete("delete-mapping"))
        assertFalse(repository.exists("delete-mapping"))
    }

    @Test
    fun `should handle expiration date`() {
        val expiration = Instant.now().plus(1, ChronoUnit.DAYS)
        val urlMapping = UrlMapping(
            id = "expiration-date",
            longUrl = "https://example.com",
            expirationDate = expiration
        )

        val saved = repository.save(urlMapping)
        val found = repository.find("expiration-date")

        val offsetExpiration = expiration.atOffset(ZoneOffset.UTC)
        val offsetFound = found!!.expirationDate!!.atOffset(ZoneOffset.UTC)

        assertEquals(expiration, saved.expirationDate)
        assertEquals(offsetExpiration.year, offsetFound.year)
        assertEquals(offsetExpiration.month, offsetFound.month)
        assertEquals(offsetExpiration.dayOfYear, offsetFound.dayOfYear)
        assertEquals(offsetExpiration.hour, offsetFound.hour)
        assertEquals(offsetExpiration.minute, offsetFound.minute)
        assertEquals(offsetExpiration.second, offsetFound.second)
    }
}
