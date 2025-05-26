package url.shortener.application.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import url.shortener.domain.repository.UrlMappingRepository
import url.shortener.logger

@Service
class UrlCleanupService(
    private val urlMappingRepository: UrlMappingRepository
) {
    companion object {
        private val logger = logger()
    }

    @Scheduled(fixedRate = 60000)
    fun cleanupExpiredUrls() {
        try {
            val expiredUrls = urlMappingRepository.findAllExpired()

            if (expiredUrls.isNotEmpty()) {
                val deletedCount = urlMappingRepository.deleteAll(expiredUrls)
                logger.info("Cleaned up $deletedCount expired URLs")
            }
        } catch (e: Exception) {
            logger.error("Failed to clean up expired URLs", e)
        }
    }
}
