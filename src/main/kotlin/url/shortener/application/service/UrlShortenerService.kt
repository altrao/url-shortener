package url.shortener.application.service

import com.datastax.oss.driver.shaded.guava.common.hash.HashFunction
import com.datastax.oss.driver.shaded.guava.common.hash.Hashing
import url.shortener.domain.model.UrlMapping
import url.shortener.domain.repository.UrlMappingRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class UrlShortenerService(
    private val urlMappingRepository: UrlMappingRepository,
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding(),
    private val hashing: HashFunction = Hashing.goodFastHash(128),
    private val slice: Int = 12
) {
    fun shortenUrl(
        longUrl: String,
        customAlias: String? = null,
        expirationDate: Instant? = null
    ): UrlMapping {
        validateUrl(longUrl)

        val shortUrl = customAlias ?: generateShortUrl(longUrl)

        if (customAlias != null && urlMappingRepository.exists(shortUrl)) {
            throw IllegalArgumentException("Custom alias already exists")
        }

        return urlMappingRepository.save(
            UrlMapping(
                id = shortUrl,
                longUrl = longUrl,
                expirationDate = expirationDate ?: Instant.now().plus(1, ChronoUnit.DAYS)
            )
        )
    }

    /**
     * Generates a short URL by creating a hash-based unique identifier from the provided long URL.
     * If a conflict occurs with an existing short URL, the function recursively appends the hash
     * to the original URL and generates a new identifier until a unique short URL is obtained.
     *
     * @param longUrl The original long URL to be shortened.
     * @return A unique shortened URL identifier.
     */
    private tailrec fun generateShortUrl(longUrl: String): String {
        val hash = encoder.encodeToString(
            hashing.hashString(longUrl, Charsets.UTF_8).toString().substring(0, slice).encodeToByteArray()
        )

        if (urlMappingRepository.exists(hash)) {
            return generateShortUrl(longUrl + longUrl.last())
        }

        return hash
    }

    private fun validateUrl(url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw IllegalArgumentException("Invalid URL format")
        }
    }

    fun getUrlMapping(shortUrl: String): UrlMapping? {
        val urlMapping = urlMappingRepository.find(shortUrl) ?: return null

        if (urlMapping.expirationDate?.isBefore(Instant.now()) == true) {
            throw IllegalArgumentException("URL has expired")
        }

        return urlMapping
    }
}
