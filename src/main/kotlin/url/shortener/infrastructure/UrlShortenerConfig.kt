package url.shortener.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Configuration class for the URL shortener application.
 *
 * This class manages configuration properties for URL shortening operations and caching behavior.
 * It includes settings for:
 * - Default expiration time for shortened URLs (defaultExpiration)
 * - Maximum allowed expiration time for URLs (maximumExpiration)
 * - Cache retention period for URL mappings (cacheTtlMinutes)
 *
 * All time-related properties are specified in minutes.
 */
@Configuration
@ConfigurationProperties(prefix = "shortener")
data class UrlShortenerConfig(
    /**
     * Specifies the default time-to-live duration, in minutes, for URL mappings stored in the database.
     */
    var defaultExpiration: Long = 1440,

    /**
     * Specifies the maximum time-to-live duration, in minutes, for URL mappings stored in the database.
     */
    var maximumExpiration: Int = 10080,

    /**
     * Specifies the time-to-live duration, in minutes, for URL mappings stored in the cache.
     */
    var cacheTtlMinutes: Long = 60,
)