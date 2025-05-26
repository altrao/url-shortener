package url.shortener.application.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.CannotAcquireLockException
import url.shortener.domain.model.UrlMapping
import url.shortener.domain.repository.UrlMappingRepository
import java.time.Instant

class UrlCleanupServiceTest {
    private val urlMappingRepository: UrlMappingRepository = mockk()
    private val meterRegistry: MeterRegistry = mockk()
    private val counter: Counter = mockk(relaxed = true)

    private val service = UrlCleanupService(urlMappingRepository, meterRegistry)

    @Test
    fun `should clean up expired URLs and record metrics`() {
        val expiredUrl = UrlMapping(
            "short-url-id",
            "https://example.com",
            expirationDate = Instant.now().minusSeconds(60)
        )

        val expiredUrls = listOf(expiredUrl)

        every { urlMappingRepository.findAllExpired() } returns expiredUrls
        every { urlMappingRepository.deleteAll(expiredUrls) } returns 1
        every { meterRegistry.counter("shortener.cleanup.deleted.count") } returns counter
        every { meterRegistry.gauge(any(), any()) } returns 1

        service.cleanupExpiredUrls()

        verify { urlMappingRepository.findAllExpired() }
        verify { urlMappingRepository.deleteAll(expiredUrls) }
        verify { meterRegistry.gauge("shortener.cleanup.expired.count", expiredUrls.size.toDouble()) }
        verify { counter.increment(1.0) }
    }

    @Test
    fun `should handle case when no expired URLs found`() {
        every { urlMappingRepository.findAllExpired() } returns emptyList()
        every { meterRegistry.gauge(any(), any()) } returns 0

        service.cleanupExpiredUrls()

        verify { urlMappingRepository.findAllExpired() }
        verify(exactly = 0) { urlMappingRepository.deleteAll(any()) }
        verify { meterRegistry.gauge("shortener.cleanup.expired.count", 0.0) }
    }

    @Test
    fun `should handle repository exception and record error metric`() {
        val exception = CannotAcquireLockException("test exception")

        every { urlMappingRepository.findAllExpired() } throws exception
        every { meterRegistry.counter("shortener.cleanup.errors") } returns counter

        assertThrows<CannotAcquireLockException> { service.cleanupExpiredUrls() }

        verify { counter.increment() }
    }
}
