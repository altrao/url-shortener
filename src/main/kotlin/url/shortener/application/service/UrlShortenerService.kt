package url.shortener.application.service

import com.datastax.oss.driver.shaded.guava.common.hash.HashFunction
import com.datastax.oss.driver.shaded.guava.common.hash.Hashing
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import url.shortener.application.exception.InvalidRequestException
import url.shortener.domain.model.UrlMapping
import url.shortener.domain.repository.UrlMappingRepository
import url.shortener.infrastructure.UrlShortenerConfig
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
    private val redis: RedisTemplate<String, UrlMapping>
) {
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val random = SecureRandom()
    private val hashing: HashFunction = Hashing.murmur3_32(random.nextInt())

    fun shortenUrl(longUrl: String, customAlias: String? = null, expirationDate: Instant? = null): UrlMapping {
        if (!longUrl.startsWith("http://") && !longUrl.startsWith("https://")) {
            throw InvalidRequestException("Invalid URL format")
        }

        if (customAlias != null && urlMappingRepository.exists(customAlias)) {
            throw InvalidRequestException("Custom alias already exists")
        }

        val urlMapping = UrlMapping(
            id = customAlias ?: generateShortUrl(longUrl),
            longUrl = longUrl,
            expirationDate = expirationDate ?: Instant.now().plus(config.defaultExpiration, ChronoUnit.MINUTES)
        )

        cache(urlMapping)

        return urlMappingRepository.save(urlMapping)
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
        val urlMapping = findAndCache(shortUrl) ?: return null

        if (urlMapping.expirationDate?.isBefore(Instant.now()) == true) {
            throw InvalidRequestException("URL has expired")
        }

        return urlMapping
    }

    private fun findAndCache(shortUrl: String): UrlMapping? {
        val cached = findInCache(shortUrl)

        if (cached != null) {
            return cached
        }

        val urlMapping = urlMappingRepository.find(shortUrl)

        if (urlMapping != null) {
            cache(urlMapping)
        }

        return urlMapping
    }

    private fun cache(urlMapping: UrlMapping) {
        redis.opsForValue().set(urlMapping.id, urlMapping, config.cacheTtlMinutes, TimeUnit.MINUTES)
    }

    private fun findInCache(shortUrl: String): UrlMapping? {
        val longUrl = redis.opsForValue().get(shortUrl)

        if (longUrl != null) {
            redis.expire(shortUrl, config.cacheTtlMinutes, TimeUnit.MINUTES)
        }

        return longUrl
    }
}
