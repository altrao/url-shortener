package url.shortener.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.core.RedisTemplate
import url.shortener.application.exception.InvalidRequestException
import url.shortener.domain.model.UrlMapping
import url.shortener.domain.repository.UrlMappingRepository
import url.shortener.infrastructure.UrlShortenerConfig
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotEquals

class UrlShortenerServiceTest {
    private val repository: UrlMappingRepository = mockk()
    private val redis: RedisTemplate<String, UrlMapping> = mockk(relaxed = true)
    private val config = UrlShortenerConfig(defaultExpiration = 60, maximumExpiration = 120, cacheTtlMinutes = 10)

    private val service = UrlShortenerService(repository, config, redis, mockk(relaxed = true))

    @BeforeEach
    fun setup() {
        every { redis.expire(any(), any(), any()) } returns true
        every { redis.opsForValue().get(any()) } returns null
        every { redis.opsForValue().set(any(), any(), any(), any()) } returns Unit
        every { redis.hasKey(any()) } returns false
    }

    @Test
    fun `should throw when url is invalid`() {
        assertThrows<InvalidRequestException> {
            service.shortenUrl("invalid-url")
        }
    }

    @Test
    fun `should generate short url when no custom alias`() {
        val longUrl = "https://example.com"

        every { repository.exists(any()) } returns false
        every { repository.save(any()) } returns UrlMapping("abc123", longUrl)

        val result = service.shortenUrl(longUrl)

        assertEquals(longUrl, result.longUrl)
        verify { repository.save(any()) }
    }

    @Test
    fun `should use custom alias when provided`() {
        val longUrl = "https://example.com"
        val customAlias = "custom"

        every { repository.exists(customAlias) } returns false
        every { repository.save(any()) } returns UrlMapping(customAlias, longUrl)

        val result = service.shortenUrl(longUrl, customAlias)

        assertEquals(customAlias, result.id)
        verify(exactly = 1) { repository.save(match { it.id == customAlias }) }
    }

    @Test
    fun `should throw when custom alias exists`() {
        val longUrl = "https://example.com"
        val customAlias = "existing"

        every { repository.exists(customAlias) } returns true

        assertThrows<InvalidRequestException> {
            service.shortenUrl(longUrl, customAlias)
        }
    }

    @Test
    fun `should set expiration date when provided`() {
        val longUrl = "https://example.com"
        val expiration = Instant.now().plus(1, ChronoUnit.DAYS)

        every { repository.exists(any()) } returns false
        every { repository.save(any()) } returns UrlMapping("abc123", longUrl, expirationDate = expiration)

        val result = service.shortenUrl(longUrl, expirationDate = expiration)

        assertEquals(expiration, result.expirationDate)
    }

    @Test
    fun `should return null when getting expired url`() {
        val shortUrl = "abc123"
        val expired = Instant.now().minus(1, ChronoUnit.DAYS)

        every { repository.find(shortUrl) } returns UrlMapping(
            shortUrl,
            "https://example.com",
            expirationDate = expired
        )

        assertNull(service.getUrlMapping(shortUrl))
    }

    @Test
    fun `should return null when url not found`() {
        val shortUrl = "not-found"

        every { repository.find(shortUrl) } returns null

        assertNull(service.getUrlMapping(shortUrl))
    }

    @Test
    fun `should handle hash collision by appending character`() {
        val longUrl = "https://example.com"

        every { repository.exists(any()) } returns false
        every { repository.save(any()) } answers { firstArg() }

        val firstMapping = service.shortenUrl(longUrl)

        every { repository.exists(firstMapping.id) } returns true

        val secondMapping = service.shortenUrl(longUrl)

        assertNotEquals(firstMapping.id, secondMapping.id)
        assertEquals(firstMapping.longUrl, secondMapping.longUrl)
        verify(exactly = 1) { repository.save(firstMapping) }
        verify(exactly = 1) { repository.save(secondMapping) }
        verify(exactly = 2) { repository.exists(firstMapping.id) }
        verify(exactly = 1) { repository.exists(secondMapping.id) }
    }

    @Test
    fun `should return cached url when available`() {
        val shortUrl = "cached123"
        val urlMapping = UrlMapping(shortUrl, "https://example.com")

        every { redis.opsForValue().get(shortUrl) } returns urlMapping
        every { repository.find(shortUrl) } returns null

        val result = service.getUrlMapping(shortUrl)

        assertEquals(urlMapping, result)
        verify { redis.opsForValue().get(shortUrl) }
        verify { redis.expire(shortUrl, config.cacheTtlMinutes, TimeUnit.MINUTES) }
        verify(exactly = 0) { repository.find(any()) }
    }

    @Test
    fun `should cache url when found in repository`() {
        val shortUrl = "new123"
        val urlMapping = UrlMapping(shortUrl, "https://example.com")

        every { redis.opsForValue().get(shortUrl) } returns null
        every { repository.find(shortUrl) } returns urlMapping

        service.getUrlMapping(shortUrl)

        verify { repository.find(shortUrl) }
        verify { redis.opsForValue().set(shortUrl, urlMapping, config.cacheTtlMinutes, TimeUnit.MINUTES) }
    }

    @Test
    fun `should cache new url mapping`() {
        val longUrl = "https://example.com"

        every { repository.exists(any()) } returns false
        every { repository.save(any()) } answers { firstArg() }

        val urlMapping = service.shortenUrl(longUrl)

        verify { redis.opsForValue().set(urlMapping.id, urlMapping, config.cacheTtlMinutes, TimeUnit.MINUTES) }
    }
}
