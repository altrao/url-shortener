package url.shortener.application.service

import io.micrometer.core.annotation.Timed
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import url.shortener.domain.repository.UrlMappingRepository
import url.shortener.logger

@Service
class UrlCleanupService(
    private val urlMappingRepository: UrlMappingRepository,
    private val meterRegistry: MeterRegistry
) {
    companion object {
        private val logger = logger()
    }

    @Scheduled(fixedRate = 60000)
    @Timed(value = "shortener.cleanup.time", description = "Time taken to clean up expired URLs")
    fun cleanupExpiredUrls() {
        try {
            val expiredUrls = urlMappingRepository.findAllExpired()

            meterRegistry.gauge("shortener.cleanup.expired.count", expiredUrls.size.toDouble())

            if (expiredUrls.isNotEmpty()) {
                logger.debug("Found ${expiredUrls.size} expired URLs to clean up")

                val deletedCount = urlMappingRepository.deleteAll(expiredUrls)

                logger.info("Successfully cleaned up $deletedCount expired URLs")
                meterRegistry.counter("shortener.cleanup.deleted.count").increment(deletedCount.toDouble())
            } else {
                logger.debug("No expired URLs found to clean up")
            }
        } catch (e: Exception) {
            logger.error("Failed to clean up expired URLs", e)
            meterRegistry.counter("shortener.cleanup.errors").increment()

            throw e
        }
    }
}
