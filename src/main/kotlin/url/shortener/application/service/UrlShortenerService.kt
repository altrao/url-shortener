package url.shortener.application.service

import com.datastax.oss.driver.shaded.guava.common.hash.HashFunction
import com.datastax.oss.driver.shaded.guava.common.hash.Hashing
import io.micrometer.core.annotation.Timed
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import url.shortener.application.exception.InvalidRequestException
import url.shortener.domain.model.UrlMapping
import url.shortener.domain.repository.UrlMappingRepository
import url.shortener.infrastructure.UrlShortenerConfig
import url.shortener.logger
import java.net.URI
import java.net.URISyntaxException
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Service that provides functionality for shortening URLs, retrieving the original URLs,
 * and caching URL mappings for optimized performance.
 *
 * This service handles ensures that custom aliases and generated hashes are unique, provides
 * time-limited expiration for URLs, and leverages caching for faster lookups.
 *
 * The service implements a two-layer caching strategy using Redis:
 * - First-level cache for frequently accessed URL mappings
 * - Automatic cache invalidation based on configured TTL
 *
 * URL validation includes:
 * - Proper URL format (http/https)
 * - Custom alias availability check
 * - Expiration date validation
 * - Automatic expiration handling
 */
@Service
class UrlShortenerService(
    private val urlMappingRepository: UrlMappingRepository,
    private val config: UrlShortenerConfig,
    private val redis: RedisTemplate<String, UrlMapping>,
    private val meterRegistry: MeterRegistry
) {
    companion object {
        private val logger = logger()
    }

    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val hashing: HashFunction = Hashing.murmur3_32(random.nextInt())

    /**
     * Shortens a given long URL into a shorter and more manageable format. Optionally, a custom alias
     * and expiration date can be provided. If no custom alias is specified, a unique identifier
     * is generated automatically. The expiration date defaults to the configured time interval if not provided.
     *
     * @param longUrl The original long URL that needs to be shortened. Must be a valid URL.
     * @param customAlias An optional custom alias for the shortened URL. If provided, it must be unique and not in use.
     * @param expirationDate An optional expiration date for the shortened URL. The shortened URL will no longer be valid after this date.
     * @return A [UrlMapping] object containing the details of the shortened URL, including the unique identifier, original long URL, and expiration details.
     * @throws InvalidRequestException If the input values are invalid, such as an invalid URL format, a duplicate custom alias, or an expiration date in the past.
     */
    @Timed(value = "shortener.shorten.time", description = "Time taken to shorten URL")
    fun shortenUrl(longUrl: String, customAlias: String? = null, expirationDate: Instant? = null): UrlMapping {
        try {
            validate(longUrl, customAlias, expirationDate)

            val urlMapping = UrlMapping(
                id = customAlias ?: generateShortUrl(longUrl),
                longUrl = longUrl,
                expirationDate = expirationDate ?: Instant.now().plus(config.defaultExpiration, ChronoUnit.MINUTES)
            )

            cache(urlMapping)
            val savedMapping = urlMappingRepository.save(urlMapping)

            meterRegistry.counter("shortener.shorten.success").increment()
            logger.debug("Successfully shortened URL: ${savedMapping.id} -> ${savedMapping.longUrl}")

            return savedMapping
        } catch (e: Exception) {
            if (e !is InvalidRequestException) {
                meterRegistry.counter("shortener.shorten.errors").increment()
                logger.error("Failed to shorten URL: $longUrl", e)
            }

            throw e
        }
    }

    /**
     * Validates the given inputs for URL shortening, ensuring that the URL is properly formatted,
     * the custom alias is not yet in use, and the expiration date is not in the past.
     *
     * @param longUrl The original long URL to validate. Must have a valid scheme (http or https) and a non-empty host.
     * @param customAlias An optional custom alias for the shortened URL which must not yet exist.
     * @param expirationDate An optional expiration date for the shortened URL which must not be in the past.
     * @throws InvalidRequestException If the URL is invalid, the alias already exists, or the expiration date is in the past.
     */
    private fun validate(longUrl: String, customAlias: String?, expirationDate: Instant?) {
        try {
            val url = URI(longUrl)

            if (!listOf("http", "https").contains(url.scheme)) {
                throw InvalidRequestException("Invalid URL scheme - must be http or https")
            }

            if (url.host.isNullOrBlank()) {
                throw InvalidRequestException("Invalid URL - missing host")
            }
        } catch (e: URISyntaxException) {
            throw InvalidRequestException("Invalid URL format")
        }

        if (expirationDate != null) {
            if (expirationDate.isBefore(Instant.now())) {
                throw InvalidRequestException("URL expiration date cannot be in the past")
            }

            if (expirationDate.isAfter(Instant.now().plus(config.maximumExpiration, ChronoUnit.MINUTES)) ) {
                throw InvalidRequestException("URL expiration date cannot be more than ${config.maximumExpiration} minutes")
            }
        }

        if (customAlias != null && urlMappingRepository.exists(customAlias)) {
            throw InvalidRequestException("Alias already exists")
        }
    }

    /**
     * Generates a short URL by creating a hash-based unique identifier from the provided long URL.
     * Uses a Murmur3 hash function to generate a hash of the input URL and then encodes
     * it using URL-safe Base64 encoding without padding.
     * In the case of hash collisions, the function recursively appends a randomly selected character
     * from the original URL to generate a new hash until a unique identifier is found.
     *
     * @param longUrl The original long URL to be shortened
     * @return A unique Base64-encoded URL-safe identifier
     */
    private tailrec fun generateShortUrl(longUrl: String): String {
        val hash = encoder.encodeToString(hashing.hashString(longUrl, Charsets.UTF_8).asBytes())

        if (urlMappingRepository.exists(hash)) {
            return generateShortUrl(longUrl + longUrl[random.nextInt(0, longUrl.lastIndex)])
        }

        return hash
    }

    fun getUrlMapping(shortUrl: String): UrlMapping? {
        return findAndCache(shortUrl).takeIf { it?.isExpired() == false }
    }

    /**
     * Attempts to find a URL mapping for the provided short URL in the cache. If the mapping is not found in the cache,
     * retrieves it from the database and stores it back in the cache for future lookups. If the mapping exists in the cache,
     * updates its expiration time.
     *
     * @param shortUrl The unique identifier for the short URL to search in the cache or database.
     * @return The corresponding `UrlMapping` if found, or null if the short URL does not exist.
     */
    @Timed(value = "shortener.lookup.time", description = "Time taken to lookup URL")
    internal fun findAndCache(shortUrl: String): UrlMapping? {
        val cached = redis.opsForValue().get(shortUrl)

        if (cached != null) {
            redis.expire(shortUrl, config.cacheTtlMinutes, TimeUnit.MINUTES)

            meterRegistry.counter("shortener.cache.hits").increment()
            logger.debug("Cache hit for short URL: $shortUrl")

            return cached
        }

        meterRegistry.counter("shortener.cache.misses").increment()

        val urlMapping = urlMappingRepository.find(shortUrl)

        if (urlMapping != null) {
            cache(urlMapping)
            logger.debug("Cached URL mapping for: $shortUrl")
        } else {
            logger.debug("URL mapping not found for: $shortUrl")
        }

        return urlMapping
    }

    private fun cache(urlMapping: UrlMapping) {
        redis.opsForValue().set(urlMapping.id, urlMapping, config.cacheTtlMinutes, TimeUnit.MINUTES)
    }
}
