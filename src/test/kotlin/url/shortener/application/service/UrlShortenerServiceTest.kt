package url.shortener.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import url.shortener.domain.model.UrlMapping
import url.shortener.domain.repository.UrlMappingRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

class UrlShortenerServiceTest {
    private val repository: UrlMappingRepository = mockk()
    private val service = UrlShortenerService(repository)

    @Test
    fun `should throw when url is invalid`() {
        assertThrows<IllegalArgumentException> {
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

        assertThrows<IllegalArgumentException> {
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
    fun `should throw when getting expired url`() {
        val shortUrl = "abc123"
        val expired = Instant.now().minus(1, ChronoUnit.DAYS)

        every { repository.find(shortUrl) } returns UrlMapping(shortUrl, "https://example.com", expirationDate = expired)

        assertThrows<IllegalArgumentException> {
            service.getUrlMapping(shortUrl)
        }
    }

    @Test
    fun `should return null when url not found`() {
        val shortUrl = "not-found"

        every { repository.find(shortUrl) } returns null

        assertNull(service.getUrlMapping(shortUrl))
    }
}
