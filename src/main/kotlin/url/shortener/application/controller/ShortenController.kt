package url.shortener.application.controller

import io.micrometer.core.annotation.Timed
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import url.shortener.application.dto.ShortenRequest
import url.shortener.application.dto.ShortenResponse
import url.shortener.application.exception.InvalidRequestException
import url.shortener.application.service.UrlShortenerService
import url.shortener.logger
import java.net.URI

@RestController
class ShortenController(
    private val urlShortenerService: UrlShortenerService,
    private val meterRegistry: MeterRegistry
) {
    companion object {
        private val logger = logger()
    }

    @Value("\${shortener.base-url}")
    private lateinit var baseUrl: String

    @PostMapping("/shorten")
    @Timed(value = "shortener.controller.shorten.time", description = "Time taken to process shorten request")
    fun shortenUrl(@RequestBody request: ShortenRequest): ResponseEntity<ShortenResponse> {
        try {
            logger.debug("Shortening URL request: ${request.longUrl}")

            val urlMapping = urlShortenerService.shortenUrl(
                longUrl = request.longUrl,
                customAlias = request.customAlias.takeIf { it?.isNotBlank() == true },
                expirationDate = request.expirationDate
            )

            val response = ShortenResponse(
                shortUrl = "$baseUrl/${urlMapping.id}",
                longUrl = urlMapping.longUrl,
                expirationDate = urlMapping.expirationDate?.toString()
            )

            meterRegistry.counter("shortener.controller.shorten.success").increment()
            logger.debug("Successfully created short URL: ${response.shortUrl}")

            return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", response.shortUrl)
                .body(response)
        } catch (e: InvalidRequestException) {
            meterRegistry.counter("shortener.controller.shorten.user-errors").increment()
            throw e
        } catch (e: Exception) {
            meterRegistry.counter("shortener.controller.shorten.errors").increment()
            logger.error("Failed to shorten URL: ${request.longUrl}", e)
            throw e
        }
    }

    @GetMapping("/{shortened}")
    @Timed(value = "shortener.controller.expand.time", description = "Time taken to process expand request")
    fun expandUrl(@PathVariable shortened: String): ResponseEntity<String> {
        try {
            logger.debug("Expanding short URL: $shortened")

            val urlMapping = urlShortenerService.getUrlMapping(shortened)
                ?: run {
                    meterRegistry.counter("shortener.controller.expand.not_found").increment()
                    logger.warn("Short URL not found: $shortened")
                    return ResponseEntity.notFound().build()
                }

            meterRegistry.counter("shortener.controller.expand.success").increment()
            logger.debug("Redirecting $shortened to ${urlMapping.longUrl}")

            return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY.value()).headers {
                it.location = URI(urlMapping.longUrl)
            }.build()
        } catch (e: InvalidRequestException) {
            meterRegistry.counter("shortener.controller.expand.user-errors").increment()
            throw e
        } catch (e: Exception) {
            meterRegistry.counter("shortener.controller.expand.errors").increment()
            logger.error("Failed to expand URL: $shortened", e)
            throw e
        }
    }
}
