package url.shortener.application.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import url.shortener.application.dto.ShortenRequest
import url.shortener.application.dto.ShortenResponse
import url.shortener.application.service.UrlShortenerService
import java.net.URI

@RestController
class ShortenController(
    private val urlShortenerService: UrlShortenerService,
) {
    @Value("\${shortener.base-url}")
    private lateinit var baseUrl: String

    @PostMapping("/shorten")
    fun shortenUrl(@RequestBody request: ShortenRequest): ShortenResponse {
        val urlMapping = urlShortenerService.shortenUrl(
            longUrl = request.longUrl,
            customAlias = request.customAlias.takeIf { it?.isNotBlank() == true },
            expirationDate = request.expirationDate
        )

        return ShortenResponse(
            shortUrl = "$baseUrl/${urlMapping.id}",
            longUrl = urlMapping.longUrl,
            expirationDate = urlMapping.expirationDate?.toString()
        )
    }

    @GetMapping("/{shortened}")
    fun expandUrl(@PathVariable shortened: String): ResponseEntity<String> {
        val urlMapping = urlShortenerService.getUrlMapping(shortened)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY.value()).headers {
            it.location = URI(urlMapping.longUrl)
        }.build()
    }
}
